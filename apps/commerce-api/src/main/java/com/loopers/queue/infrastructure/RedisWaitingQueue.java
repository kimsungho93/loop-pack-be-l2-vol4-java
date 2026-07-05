package com.loopers.queue.infrastructure;

import com.loopers.config.redis.RedisConfig;
import com.loopers.queue.application.QueueEnterResult;
import com.loopers.queue.application.TokenConsumeResult;
import com.loopers.queue.application.WaitingQueue;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class RedisWaitingQueue implements WaitingQueue {

    private static final String WAITING_KEY = "queue:waiting";
    private static final String TOKEN_KEY_PREFIX = "queue:entry-token:";
    private static final String USED_MARKER = "USED";

    // 줄 세우기와 순번 조회를 원자적으로 묶는다. ZADD 와 ZRANK 사이에 입장 배치(ZPOPMIN)가
    // 끼어들면 방금 세운 사용자의 rank 가 nil 이 되므로 개별 명령으로 나누면 안 된다.
    // NX: 이미 줄에 선 사용자가 재진입해도 기존 score(순번)를 유지한다.
    // ARGV[1]=score(진입 시각 ms), ARGV[2]=member(userId)
    private static final RedisScript<List> ENTER_SCRIPT = RedisScript.of("""
        redis.call('ZADD', KEYS[1], 'NX', ARGV[1], ARGV[2])
        local rank = redis.call('ZRANK', KEYS[1], ARGV[2])
        return {rank, redis.call('ZCARD', KEYS[1])}
        """, List.class);

    // 대기열 pop과 토큰 저장을 원자적으로 묶는다. 토큰 값은 Java에서 생성해 ARGV로 전달한다.
    // ARGV[1]=토큰 TTL(ms), ARGV[2]=토큰 키 prefix, ARGV[3..]=토큰 값
    private static final RedisScript<Long> ADMIT_SCRIPT = RedisScript.of("""
        local batch = #ARGV - 2
        if batch <= 0 then
            return 0
        end
        local popped = redis.call('ZPOPMIN', KEYS[1], batch)
        local admitted = 0
        for i = 1, #popped, 2 do
            admitted = admitted + 1
            redis.call('SET', ARGV[2] .. popped[i], ARGV[2 + admitted], 'PX', ARGV[1])
        end
        return admitted
        """, Long.class);

    // 토큰 확인과 상태 전이를 원자적으로 묶는다. 삭제 대신 'USED' 마커를 남겨
    // "성공 후 재시도"(ALREADY_USED)와 "무효 토큰"(INVALID)을 구분한다.
    // ARGV[1]=토큰 값, ARGV[2]=마커 TTL(ms)
    private static final RedisScript<Long> CONSUME_TOKEN_SCRIPT = RedisScript.of("""
        local value = redis.call('GET', KEYS[1])
        if value == ARGV[1] then
            redis.call('SET', KEYS[1], 'USED', 'PX', ARGV[2])
            return 1
        end
        if value == 'USED' then
            return 2
        end
        return 0
        """, Long.class);

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisTemplate<String, String> masterRedisTemplate;

    public RedisWaitingQueue(
        RedisTemplate<String, String> redisTemplate,
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> masterRedisTemplate
    ) {
        this.redisTemplate = redisTemplate;
        this.masterRedisTemplate = masterRedisTemplate;
    }

    @Override
    @SuppressWarnings("unchecked")
    public QueueEnterResult enter(long userId, long enterAtMillis) {
        List<Long> result = masterRedisTemplate.execute(
            ENTER_SCRIPT,
            List.of(WAITING_KEY),
            String.valueOf(enterAtMillis),
            String.valueOf(userId)
        );
        return new QueueEnterResult(result.get(0) + 1, result.get(1));
    }

    @Override
    public Optional<Long> findRank(long userId) {
        return Optional.ofNullable(redisTemplate.opsForZSet().rank(WAITING_KEY, String.valueOf(userId)));
    }

    @Override
    public long countWaiting() {
        Long count = redisTemplate.opsForZSet().zCard(WAITING_KEY);
        return count == null ? 0L : count;
    }

    @Override
    public int admit(List<String> tokens, Duration tokenTtl) {
        List<String> args = new ArrayList<>();
        args.add(String.valueOf(tokenTtl.toMillis()));
        args.add(TOKEN_KEY_PREFIX);
        args.addAll(tokens);
        Long admitted = masterRedisTemplate.execute(ADMIT_SCRIPT, List.of(WAITING_KEY), args.toArray());
        return admitted == null ? 0 : admitted.intValue();
    }

    @Override
    public Optional<String> findToken(long userId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(tokenKey(userId)))
            .filter(value -> !USED_MARKER.equals(value));
    }

    @Override
    public TokenConsumeResult consumeToken(long userId, String token, Duration usedMarkerTtl) {
        Long result = masterRedisTemplate.execute(
            CONSUME_TOKEN_SCRIPT,
            List.of(tokenKey(userId)),
            token,
            String.valueOf(usedMarkerTtl.toMillis())
        );
        if (result != null && result == 1L) {
            return TokenConsumeResult.CONSUMED;
        }
        if (result != null && result == 2L) {
            return TokenConsumeResult.ALREADY_USED;
        }
        return TokenConsumeResult.INVALID;
    }

    @Override
    public boolean isTokenUsed(long userId) {
        return USED_MARKER.equals(redisTemplate.opsForValue().get(tokenKey(userId)));
    }

    private String tokenKey(long userId) {
        return TOKEN_KEY_PREFIX + userId;
    }
}
