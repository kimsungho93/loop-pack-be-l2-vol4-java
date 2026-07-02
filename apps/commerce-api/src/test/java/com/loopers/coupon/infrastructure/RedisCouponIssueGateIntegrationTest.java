package com.loopers.coupon.infrastructure;

import com.loopers.coupon.application.CouponIssueGate;
import com.loopers.coupon.application.CouponIssueGateResult;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.ZonedDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class RedisCouponIssueGateIntegrationTest {

    private static final Long COUPON_TEMPLATE_ID = 11L;
    private static final ZonedDateTime EXPIRED_AT = ZonedDateTime.parse("2026-12-31T23:59:59+09:00");
    private static final ZonedDateTime PAST_EXPIRED_AT = ZonedDateTime.parse("2026-01-01T00:00:00+09:00");

    private final CouponIssueGate couponIssueGate;
    private final RedisCleanUp redisCleanUp;

    @Autowired
    RedisCouponIssueGateIntegrationTest(CouponIssueGate couponIssueGate, RedisCleanUp redisCleanUp) {
        this.couponIssueGate = couponIssueGate;
        this.redisCleanUp = redisCleanUp;
    }

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("게이트 통과를 판정할 때 ")
    @Nested
    class TryPass {

        @DisplayName("카운터가 초기화되지 않은 첫 요청도, 통과한다.")
        @Test
        void passesFirstRequest_whenCounterIsNotInitialized() {
            // arrange
            Long userId = 101L;

            // act
            CouponIssueGateResult result = couponIssueGate.tryPass(userId, COUPON_TEMPLATE_ID, 100, EXPIRED_AT);

            // assert
            assertThat(result).isEqualTo(CouponIssueGateResult.PASSED);
        }

        @DisplayName("이미 통과한 사용자가 다시 요청하면, DUPLICATE로 거절한다.")
        @Test
        void rejectsAsDuplicate_whenUserAlreadyPassed() {
            // arrange
            Long userId = 101L;
            couponIssueGate.tryPass(userId, COUPON_TEMPLATE_ID, 100, EXPIRED_AT);

            // act
            CouponIssueGateResult result = couponIssueGate.tryPass(userId, COUPON_TEMPLATE_ID, 100, EXPIRED_AT);

            // assert
            assertThat(result).isEqualTo(CouponIssueGateResult.DUPLICATE);
        }

        @DisplayName("수량이 모두 소진되면, SOLD_OUT으로 거절한다.")
        @Test
        void rejectsAsSoldOut_whenQuantityIsExhausted() {
            // arrange
            couponIssueGate.tryPass(101L, COUPON_TEMPLATE_ID, 1, EXPIRED_AT);

            // act
            CouponIssueGateResult result = couponIssueGate.tryPass(102L, COUPON_TEMPLATE_ID, 1, EXPIRED_AT);

            // assert
            assertThat(result).isEqualTo(CouponIssueGateResult.SOLD_OUT);
        }

        @DisplayName("만료 시각이 이미 지난 쿠폰이면, 통과시키지 않는다.")
        @Test
        void rejects_whenExpireAtIsInThePast() {
            // arrange
            Long userId = 101L;

            // act
            CouponIssueGateResult result = couponIssueGate.tryPass(userId, COUPON_TEMPLATE_ID, 100, PAST_EXPIRED_AT);

            // assert
            assertThat(result).isEqualTo(CouponIssueGateResult.SOLD_OUT);
        }
    }

    @DisplayName("동시에 게이트 통과를 판정할 때 ")
    @Nested
    class TryPassConcurrently {

        @DisplayName("총 수량보다 많은 사용자가 동시에 요청하면, 정확히 총 수량만큼만 통과한다.")
        @Test
        void passesExactlyTotalQuantity_whenConcurrentRequestsExceedQuantity() throws InterruptedException {
            // arrange
            int totalQuantity = 100;
            int concurrentRequests = 200;
            ExecutorService executor = Executors.newFixedThreadPool(20);
            CountDownLatch latch = new CountDownLatch(concurrentRequests);
            AtomicInteger passedCount = new AtomicInteger();
            AtomicInteger rejectedCount = new AtomicInteger();

            // act
            for (long userId = 1; userId <= concurrentRequests; userId++) {
                long currentUserId = userId;
                executor.submit(() -> {
                    try {
                        CouponIssueGateResult result =
                            couponIssueGate.tryPass(currentUserId, COUPON_TEMPLATE_ID, totalQuantity, EXPIRED_AT);
                        if (result == CouponIssueGateResult.PASSED) {
                            passedCount.incrementAndGet();
                        } else {
                            rejectedCount.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
            executor.shutdown();

            // assert
            assertAll(
                () -> assertThat(passedCount.get()).isEqualTo(totalQuantity),
                () -> assertThat(rejectedCount.get()).isEqualTo(concurrentRequests - totalQuantity)
            );
        }
    }
}
