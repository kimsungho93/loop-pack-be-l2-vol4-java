package com.loopers.queue.infrastructure;

import com.loopers.config.redis.RedisConfig;
import com.loopers.queue.application.QueueEnterResult;
import com.loopers.queue.application.TokenConsumeResult;
import com.loopers.queue.application.WaitingQueue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class RedisWaitingQueue implements WaitingQueue {

    private static final String WAITING_KEY = "queue:waiting";
    private static final String TOKEN_KEY_PREFIX = "queue:entry-token:";
    private static final String WAITING_TOKEN_KEY_PREFIX = "queue:waiting-token:";
    // 소비된 토큰 자리에 남기는 sentinel. 'USED:{원본 토큰}' 형태로 출처를 남겨
    // 다른 소비의 마커를 복구하는 것(유령 복구)을 막는다. 토큰 값은 UUID 형식이라 충돌하지 않는다.
    private static final String USED_MARKER_PREFIX = "USED:";

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

    // 토큰 확인과 상태 전이를 원자적으로 묶는다. 삭제 대신 'USED:{토큰}' 마커를 남겨
    // "성공 후 재시도"(ALREADY_USED)와 "무효 토큰"(INVALID)을 구분하고, 마커에 소비한
    // 토큰의 출처를 남겨 다른 소비의 마커를 복구하는 유령 복구를 막는다.
    // ARGV[1]=토큰 값, ARGV[2]=마커 TTL(ms)
    // value ~= false 가드: Redis GET은 키가 없으면 false 를 반환하는데, string.sub(false, ...)는 에러를 던진다.
    private static final RedisScript<Long> CONSUME_TOKEN_SCRIPT = RedisScript.of("""
        local value = redis.call('GET', KEYS[1])
        if value == ARGV[1] then
            redis.call('SET', KEYS[1], 'USED:' .. ARGV[1], 'PX', ARGV[2])
            return 1
        end
        if value ~= false and string.sub(value, 1, 5) == 'USED:' then
            return 2
        end
        return 0
        """, Long.class);

    // 실패한 주문의 토큰을 되돌린다. 마커가 만료된 뒤 SET 하면 토큰이 부활하므로
    // 자기 토큰의 마커일 때만 복구하는 가드를 스크립트 안에 둔다.
    // ARGV[1]=토큰 값, ARGV[2]=토큰 TTL(ms)
    private static final RedisScript<Long> RESTORE_TOKEN_SCRIPT = RedisScript.of("""
        if redis.call('GET', KEYS[1]) == 'USED:' .. ARGV[1] then
            redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])
            return 1
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
    public void saveWaitingToken(String waitingToken, long userId, Duration ttl) {
        masterRedisTemplate.opsForValue().set(waitingTokenKey(waitingToken), String.valueOf(userId), ttl);
    }

    @Override
    public Optional<Long> findUserIdByWaitingToken(String waitingToken) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(waitingTokenKey(waitingToken)))
            .map(Long::parseLong);
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
            .filter(value -> !value.startsWith(USED_MARKER_PREFIX));
    }

    @Override
    public TokenConsumeResult consumeToken(long userId, String token, Duration usedMarkerTtl) {
        Long result;
        try {
            result = masterRedisTemplate.execute(
                CONSUME_TOKEN_SCRIPT,
                List.of(tokenKey(userId)),
                token,
                String.valueOf(usedMarkerTtl.toMillis())
            );
        } catch (DataAccessException e) {
            // "아니오"라고 답한 것(INVALID)과 달리 답을 못한 것 — 통과/거절 정책은 게이트가 정하도록 판정만 전달한다.
            log.error("Failed to consume queue token: store unavailable. userId={}", userId, e);
            return TokenConsumeResult.UNDECIDED;
        }
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
        String value = redisTemplate.opsForValue().get(tokenKey(userId));
        return value != null && value.startsWith(USED_MARKER_PREFIX);
    }

    @Override
    public boolean restoreToken(long userId, String token, Duration tokenTtl) {
        Long restored;
        try {
            restored = masterRedisTemplate.execute(
                RESTORE_TOKEN_SCRIPT,
                List.of(tokenKey(userId)),
                token,
                String.valueOf(tokenTtl.toMillis())
            );
        } catch (DataAccessException e) {
            // 복구는 최선 노력 — 저장소 장애면 실패로 처리하고 사용자는 회복 후 재진입한다.
            log.error("Failed to restore queue token: store unavailable. userId={}", userId, e);
            return false;
        }
        return restored != null && restored == 1L;
    }

    private String tokenKey(long userId) {
        return TOKEN_KEY_PREFIX + userId;
    }

    private String waitingTokenKey(String waitingToken) {
        return WAITING_TOKEN_KEY_PREFIX + waitingToken;
    }
}
