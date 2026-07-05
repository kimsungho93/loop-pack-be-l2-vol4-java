package com.loopers.queue.infrastructure;

import com.loopers.queue.application.QueueEnterResult;
import com.loopers.queue.application.TokenConsumeResult;
import com.loopers.queue.application.WaitingQueue;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class RedisWaitingQueueIntegrationTest {

    private final WaitingQueue waitingQueue;
    private final RedisCleanUp redisCleanUp;
    private final RedisTemplate<String, String> redisTemplate;

    @Autowired
    RedisWaitingQueueIntegrationTest(
        WaitingQueue waitingQueue,
        RedisCleanUp redisCleanUp,
        RedisTemplate<String, String> redisTemplate
    ) {
        this.waitingQueue = waitingQueue;
        this.redisCleanUp = redisCleanUp;
        this.redisTemplate = redisTemplate;
    }

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("대기열에 진입할 때 ")
    @Nested
    class Enter {

        @DisplayName("처음 진입하면, 1번 순번을 받는다.")
        @Test
        void returnsFirstPosition_whenQueueIsEmpty() {
            // act
            QueueEnterResult result = waitingQueue.enter(101L, 1_000L);

            // assert
            assertAll(
                () -> assertThat(result.position()).isEqualTo(1L),
                () -> assertThat(result.totalWaiting()).isEqualTo(1L)
            );
        }

        @DisplayName("여러 명이 진입하면, 도착 순서대로 순번을 받는다.")
        @Test
        void assignsPositionsInArrivalOrder() {
            // arrange
            waitingQueue.enter(101L, 1_000L);

            // act
            QueueEnterResult result = waitingQueue.enter(102L, 2_000L);

            // assert
            assertAll(
                () -> assertThat(result.position()).isEqualTo(2L),
                () -> assertThat(result.totalWaiting()).isEqualTo(2L)
            );
        }

        @DisplayName("이미 줄에 선 사용자가 다시 진입해도, 기존 순번이 유지된다.")
        @Test
        void keepsOriginalPosition_whenUserEntersAgain() {
            // arrange
            waitingQueue.enter(101L, 1_000L);
            waitingQueue.enter(102L, 2_000L);

            // act — 더 늦은 시각으로 재진입해도 순번이 뒤로 밀리지 않아야 한다.
            QueueEnterResult result = waitingQueue.enter(101L, 3_000L);

            // assert
            assertAll(
                () -> assertThat(result.position()).isEqualTo(1L),
                () -> assertThat(result.totalWaiting()).isEqualTo(2L)
            );
        }
    }

    @DisplayName("대기 순번을 조회할 때 ")
    @Nested
    class FindRank {

        @DisplayName("줄에 선 사용자는, 0부터 시작하는 순번을 반환한다.")
        @Test
        void returnsZeroBasedRank_whenUserIsWaiting() {
            // arrange
            waitingQueue.enter(101L, 1_000L);
            waitingQueue.enter(102L, 2_000L);

            // act
            Optional<Long> rank = waitingQueue.findRank(102L);

            // assert
            assertThat(rank).contains(1L);
        }

        @DisplayName("줄에 없는 사용자는, 빈 값을 반환한다.")
        @Test
        void returnsEmpty_whenUserIsNotWaiting() {
            // act
            Optional<Long> rank = waitingQueue.findRank(999L);

            // assert
            assertThat(rank).isEmpty();
        }
    }

    @DisplayName("전체 대기 인원을 조회할 때 ")
    @Nested
    class CountWaiting {

        @DisplayName("줄에 선 인원 수를 반환한다.")
        @Test
        void returnsWaitingCount() {
            // arrange
            waitingQueue.enter(101L, 1_000L);
            waitingQueue.enter(102L, 2_000L);
            waitingQueue.enter(103L, 3_000L);

            // act
            long count = waitingQueue.countWaiting();

            // assert
            assertThat(count).isEqualTo(3L);
        }
    }

    @DisplayName("대기열에서 입장시킬 때 ")
    @Nested
    class Admit {

        private static final Duration TOKEN_TTL = Duration.ofMinutes(5);

        @DisplayName("대기 인원이 배치보다 많으면, 배치 크기만큼만 앞에서부터 입장시킨다.")
        @Test
        void admitsBatchSizeFromFront_whenWaitingExceedsBatch() {
            // arrange
            waitingQueue.enter(101L, 1_000L);
            waitingQueue.enter(102L, 2_000L);
            waitingQueue.enter(103L, 3_000L);

            // act
            int admitted = waitingQueue.admit(List.of("token-a", "token-b"), TOKEN_TTL);

            // assert
            assertAll(
                () -> assertThat(admitted).isEqualTo(2),
                () -> assertThat(waitingQueue.findToken(101L)).contains("token-a"),
                () -> assertThat(waitingQueue.findToken(102L)).contains("token-b"),
                () -> assertThat(waitingQueue.findToken(103L)).isEmpty(),
                () -> assertThat(waitingQueue.findRank(103L)).contains(0L),
                () -> assertThat(waitingQueue.countWaiting()).isEqualTo(1L)
            );
        }

        @DisplayName("대기 인원이 배치보다 적으면, 있는 만큼만 입장시킨다.")
        @Test
        void admitsOnlyWaitingUsers_whenWaitingIsLessThanBatch() {
            // arrange
            waitingQueue.enter(101L, 1_000L);

            // act
            int admitted = waitingQueue.admit(List.of("token-a", "token-b"), TOKEN_TTL);

            // assert
            assertAll(
                () -> assertThat(admitted).isEqualTo(1),
                () -> assertThat(waitingQueue.findToken(101L)).contains("token-a"),
                () -> assertThat(waitingQueue.countWaiting()).isZero()
            );
        }

        @DisplayName("대기열이 비어 있으면, 아무도 입장시키지 않는다.")
        @Test
        void admitsNobody_whenQueueIsEmpty() {
            // act
            int admitted = waitingQueue.admit(List.of("token-a"), TOKEN_TTL);

            // assert
            assertThat(admitted).isZero();
        }

        @DisplayName("발급된 토큰에는 만료 시간이 설정된다.")
        @Test
        void setsTtlOnIssuedToken() {
            // arrange
            waitingQueue.enter(101L, 1_000L);

            // act
            waitingQueue.admit(List.of("token-a"), TOKEN_TTL);

            // assert
            assertThat(redisTemplate.getExpire("queue:entry-token:101")).isPositive();
        }

        @DisplayName("만료 시간이 지나면, 토큰이 무효화된다.")
        @Test
        void invalidatesToken_afterTtlPassed() throws InterruptedException {
            // arrange
            waitingQueue.enter(101L, 1_000L);
            waitingQueue.admit(List.of("token-a"), Duration.ofMillis(100));

            // act
            Thread.sleep(300);

            // assert
            assertThat(waitingQueue.findToken(101L)).isEmpty();
        }
    }

    @DisplayName("입장 토큰을 소비할 때 ")
    @Nested
    class ConsumeToken {

        private static final Duration TOKEN_TTL = Duration.ofMinutes(5);

        @DisplayName("유효한 토큰이면, 소비하고 사용됨 마커로 바꾼다.")
        @Test
        void consumesTokenAndMarksUsed_whenTokenIsValid() {
            // arrange
            waitingQueue.enter(101L, 1_000L);
            waitingQueue.admit(List.of("token-a"), TOKEN_TTL);

            // act
            TokenConsumeResult result = waitingQueue.consumeToken(101L, "token-a", TOKEN_TTL);

            // assert — findToken 은 마커를 토큰으로 노출하지 않는다.
            assertAll(
                () -> assertThat(result).isEqualTo(TokenConsumeResult.CONSUMED),
                () -> assertThat(waitingQueue.findToken(101L)).isEmpty(),
                () -> assertThat(waitingQueue.isTokenUsed(101L)).isTrue()
            );
        }

        @DisplayName("이미 소비한 토큰을 다시 소비하면, ALREADY_USED 를 반환한다.")
        @Test
        void returnsAlreadyUsed_whenTokenAlreadyConsumed() {
            // arrange
            waitingQueue.enter(101L, 1_000L);
            waitingQueue.admit(List.of("token-a"), TOKEN_TTL);
            waitingQueue.consumeToken(101L, "token-a", TOKEN_TTL);

            // act
            TokenConsumeResult result = waitingQueue.consumeToken(101L, "token-a", TOKEN_TTL);

            // assert
            assertThat(result).isEqualTo(TokenConsumeResult.ALREADY_USED);
        }

        @DisplayName("토큰 값이 다르면, INVALID 를 반환하고 기존 토큰은 유지한다.")
        @Test
        void returnsInvalidAndKeepsToken_whenTokenDoesNotMatch() {
            // arrange
            waitingQueue.enter(101L, 1_000L);
            waitingQueue.admit(List.of("token-a"), TOKEN_TTL);

            // act
            TokenConsumeResult result = waitingQueue.consumeToken(101L, "wrong-token", TOKEN_TTL);

            // assert
            assertAll(
                () -> assertThat(result).isEqualTo(TokenConsumeResult.INVALID),
                () -> assertThat(waitingQueue.findToken(101L)).contains("token-a")
            );
        }

        @DisplayName("발급된 토큰이 없으면, INVALID 를 반환한다.")
        @Test
        void returnsInvalid_whenTokenDoesNotExist() {
            // act
            TokenConsumeResult result = waitingQueue.consumeToken(999L, "token-a", TOKEN_TTL);

            // assert
            assertThat(result).isEqualTo(TokenConsumeResult.INVALID);
        }

        @DisplayName("사용됨 마커의 TTL 이 지나면, 재소비는 ALREADY_USED 가 아니라 INVALID 다.")
        @Test
        void returnsInvalid_whenUsedMarkerExpired() throws InterruptedException {
            // arrange — 마커 TTL 을 짧게 줘서 만료를 재현한다.
            waitingQueue.enter(101L, 1_000L);
            waitingQueue.admit(List.of("token-a"), TOKEN_TTL);
            waitingQueue.consumeToken(101L, "token-a", Duration.ofMillis(100));

            // act
            Thread.sleep(300);
            TokenConsumeResult result = waitingQueue.consumeToken(101L, "token-a", TOKEN_TTL);

            // assert
            assertThat(result).isEqualTo(TokenConsumeResult.INVALID);
        }

        @DisplayName("발급만 되고 소비되지 않은 토큰은, 사용됨 상태가 아니다.")
        @Test
        void isTokenUsedReturnsFalse_whenTokenIsIssuedButNotConsumed() {
            // arrange
            waitingQueue.enter(101L, 1_000L);
            waitingQueue.admit(List.of("token-a"), TOKEN_TTL);

            // act & assert
            assertThat(waitingQueue.isTokenUsed(101L)).isFalse();
        }
    }

    @DisplayName("동시에 요청이 몰릴 때 ")
    @Nested
    class Concurrency {

        // score 가 밀리초 단위라 같은 밀리초에 도착한 요청 간 순서는 보장하지 않는다.
        // 검증 범위는 "정확한 순서"가 아니라 무손실(전량 수용)과 무중복(1인 1자리)이다.
        @DisplayName("배치 크기를 훨씬 넘는 인원이 동시에 진입해도, 유실 없이 전원 줄에 선다.")
        @Test
        void acceptsAllUsersWithoutLoss_whenConcurrentEntersExceedBatch() throws InterruptedException {
            // arrange
            int concurrentUsers = 200;
            ExecutorService executor = Executors.newFixedThreadPool(20);
            CountDownLatch latch = new CountDownLatch(concurrentUsers);

            // act
            for (long userId = 1; userId <= concurrentUsers; userId++) {
                long currentUserId = userId;
                executor.submit(() -> {
                    try {
                        waitingQueue.enter(currentUserId, System.currentTimeMillis());
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
            executor.shutdown();

            // assert
            assertThat(waitingQueue.countWaiting()).isEqualTo(concurrentUsers);
        }

        @DisplayName("여러 스레드가 동시에 입장 배치를 실행해도, 정확히 대기 인원만큼만 입장한다.")
        @Test
        void admitsExactlyWaitingCount_whenBatchesRunConcurrently() throws InterruptedException {
            // arrange
            int waitingUsers = 200;
            int batchSize = 10;
            int batchRuns = 40; // 배치 합계(400)가 대기 인원(200)보다 커도 초과 입장은 없어야 한다.
            for (long userId = 1; userId <= waitingUsers; userId++) {
                waitingQueue.enter(userId, userId);
            }
            ExecutorService executor = Executors.newFixedThreadPool(8);
            CountDownLatch latch = new CountDownLatch(batchRuns);
            AtomicInteger totalAdmitted = new AtomicInteger();

            // act
            for (int run = 0; run < batchRuns; run++) {
                int currentRun = run;
                executor.submit(() -> {
                    try {
                        List<String> tokens = IntStream.range(0, batchSize)
                            .mapToObj(i -> "token-" + currentRun + "-" + i)
                            .toList();
                        totalAdmitted.addAndGet(waitingQueue.admit(tokens, Duration.ofMinutes(5)));
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
            executor.shutdown();

            // assert — 전원이 정확히 한 번씩 입장했고(무손실·무중복), 대기열은 비어 있다.
            assertAll(
                () -> assertThat(totalAdmitted.get()).isEqualTo(waitingUsers),
                () -> assertThat(waitingQueue.countWaiting()).isZero(),
                () -> {
                    for (long userId = 1; userId <= waitingUsers; userId++) {
                        assertThat(waitingQueue.findToken(userId)).isPresent();
                    }
                }
            );
        }
    }
}
