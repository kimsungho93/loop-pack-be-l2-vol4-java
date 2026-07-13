package com.loopers.ranking.infrastructure;

import com.loopers.config.redis.RedisConfig;
import com.loopers.ranking.RankingExpirationPolicy;
import com.loopers.ranking.RankingRedisKey;
import com.loopers.ranking.application.RankingScoreBatch;
import com.loopers.ranking.application.RankingScoreDelta;
import com.loopers.ranking.application.RankingScoreRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class RedisRankingScoreRepository implements RankingScoreRepository {

    private static final RedisScript<Long> APPLY_SCRIPT = RedisScript.of("""
        if #KEYS ~= 3 or #ARGV < 4 or ((#ARGV - 2) % 2) ~= 0 then
            return redis.error_reply('invalid ranking score arguments')
        end

        local function validate_key_type(key, expected)
            local key_type = redis.call('TYPE', key).ok
            if key_type ~= 'none' and key_type ~= expected then
                return false
            end
            return true
        end

        if not validate_key_type(KEYS[1], 'zset')
            or not validate_key_type(KEYS[2], 'zset')
            or not validate_key_type(KEYS[3], 'set') then
            return redis.error_reply('invalid ranking key type')
        end

        local expires_at = tonumber(ARGV[2])
        if expires_at == nil then
            return redis.error_reply('invalid expiration')
        end

        for index = 3, #ARGV, 2 do
            local event_id = ARGV[index]
            local score_delta = tonumber(ARGV[index + 1])
            if event_id == '' or score_delta == nil or score_delta ~= score_delta then
                return redis.error_reply('invalid ranking score delta')
            end
        end

        local unseen_event_ids = {}
        local unseen_event_lookup = {}
        local total_delta = 0
        for index = 3, #ARGV, 2 do
            local event_id = ARGV[index]
            if unseen_event_lookup[event_id] == nil
                and redis.call('SISMEMBER', KEYS[3], event_id) == 0 then
                total_delta = total_delta + tonumber(ARGV[index + 1])
                table.insert(unseen_event_ids, event_id)
                unseen_event_lookup[event_id] = true
            end
        end

        if #unseen_event_ids == 0 then
            return 0
        end

        redis.call('ZINCRBY', KEYS[1], total_delta, ARGV[1])
        redis.call('ZINCRBY', KEYS[2], total_delta, ARGV[1])
        for _, event_id in ipairs(unseen_event_ids) do
            redis.call('SADD', KEYS[3], event_id)
        end
        redis.call('PEXPIREAT', KEYS[1], expires_at)
        redis.call('PEXPIREAT', KEYS[2], expires_at)
        redis.call('PEXPIREAT', KEYS[3], expires_at)
        return #unseen_event_ids
        """, Long.class);

    private final RedisTemplate<String, String> masterRedisTemplate;

    public RedisRankingScoreRepository(
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> masterRedisTemplate
    ) {
        this.masterRedisTemplate = masterRedisTemplate;
    }

    @Override
    public long apply(RankingScoreBatch batch) {
        List<String> keys = List.of(
            RankingRedisKey.daily(batch.window().date()),
            RankingRedisKey.hourly(batch.window()),
            RankingRedisKey.handled(batch.window().date())
        );
        List<String> arguments = arguments(batch);

        Long appliedCount = masterRedisTemplate.execute(APPLY_SCRIPT, keys, arguments.toArray());
        if (appliedCount == null) {
            throw new IllegalStateException("Ranking score script returned null");
        }
        return appliedCount;
    }

    private List<String> arguments(RankingScoreBatch batch) {
        List<String> arguments = new ArrayList<>(2 + batch.deltas().size() * 2);
        arguments.add(String.valueOf(batch.productId()));
        arguments.add(String.valueOf(
            RankingExpirationPolicy.expiresAt(batch.window().date()).toEpochMilli()
        ));
        for (RankingScoreDelta delta : batch.deltas()) {
            arguments.add(delta.eventId());
            arguments.add(String.valueOf(delta.scoreDelta()));
        }
        return arguments;
    }
}
