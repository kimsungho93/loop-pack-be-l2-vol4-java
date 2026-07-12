package com.loopers.queue.infrastructure;

import com.loopers.queue.application.TokenConsumeResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Redis 가 응답하지 못할 때(연결 장애)의 판정 번역을 검증한다.
 * "Redis 가 '아니오'라고 답한 것"(INVALID)과 "Redis 가 답을 못한 것"(UNDECIDED)의 구분이 핵심.
 */
class RedisWaitingQueueFailureTest {

    @SuppressWarnings("unchecked")
    private final RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final RedisTemplate<String, String> masterRedisTemplate = mock(RedisTemplate.class);
    private final RedisWaitingQueue waitingQueue = new RedisWaitingQueue(redisTemplate, masterRedisTemplate);

    @DisplayName("토큰 소비 중 Redis 연결이 실패하면, 예외 대신 UNDECIDED로 판정한다.")
    @Test
    void returnsUndecided_whenRedisIsUnreachableOnConsume() {
        // arrange
        when(masterRedisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
            .thenThrow(new RedisConnectionFailureException("redis down"));

        // act
        TokenConsumeResult result = waitingQueue.consumeToken(101L, "token-a", Duration.ofMinutes(5));

        // assert
        assertThat(result).isEqualTo(TokenConsumeResult.UNDECIDED);
    }

    @DisplayName("토큰 복구 중 Redis 연결이 실패하면, 예외 대신 복구 실패로 판정한다.")
    @Test
    void returnsFalse_whenRedisIsUnreachableOnRestore() {
        // arrange
        when(masterRedisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
            .thenThrow(new RedisConnectionFailureException("redis down"));

        // act
        boolean restored = waitingQueue.restoreToken(101L, "token-a", Duration.ofMinutes(5));

        // assert
        assertThat(restored).isFalse();
    }
}
