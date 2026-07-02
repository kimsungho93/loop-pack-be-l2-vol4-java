package com.loopers.coupon.domain;

import com.loopers.coupon.domain.policy.FixedCouponDiscountPolicy;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class CouponIssueRequestServiceIntegrationTest {

    private static final String COUPON_NAME = "1주년 쿠폰";
    private static final ZonedDateTime EXPIRED_AT = ZonedDateTime.parse("2026-12-31T23:59:59+09:00");
    private static final ZonedDateTime PAST_EXPIRED_AT = ZonedDateTime.parse("2026-01-01T00:00:00+09:00");
    private static final ZonedDateTime REQUESTED_AT = ZonedDateTime.parse("2026-07-03T10:00:00+09:00");
    private static final FixedCouponDiscountPolicy FIXED_POLICY = new FixedCouponDiscountPolicy();

    private final CouponIssueRequestService couponIssueRequestService;
    private final CouponIssueRequestRepository couponIssueRequestRepository;
    private final CouponTemplateRepository couponTemplateRepository;
    private final UserCouponRepository userCouponRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    CouponIssueRequestServiceIntegrationTest(
        CouponIssueRequestService couponIssueRequestService,
        CouponIssueRequestRepository couponIssueRequestRepository,
        CouponTemplateRepository couponTemplateRepository,
        UserCouponRepository userCouponRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.couponIssueRequestService = couponIssueRequestService;
        this.couponIssueRequestRepository = couponIssueRequestRepository;
        this.couponTemplateRepository = couponTemplateRepository;
        this.userCouponRepository = userCouponRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("발급 요청을 처리할 때 ")
    @Nested
    class Process {

        @DisplayName("수량이 남아 있으면, 쿠폰을 발급하고 ISSUED 상태로 전이한다.")
        @Test
        void issuesCoupon_whenQuantityRemains() {
            // arrange
            Long userId = 101L;
            CouponTemplate template = couponTemplateRepository.save(createTemplate(10, EXPIRED_AT));
            CouponIssueRequest request = couponIssueRequestRepository.save(
                CouponIssueRequest.request(userId, template.getId(), REQUESTED_AT)
            );

            // act
            couponIssueRequestService.process(request.getId());

            // assert
            CouponIssueRequest processed = couponIssueRequestRepository.findById(request.getId()).orElseThrow();
            CouponTemplate found = couponTemplateRepository.findActiveById(template.getId()).orElseThrow();
            assertAll(
                () -> assertThat(processed.getStatus()).isEqualTo(CouponIssueRequestStatus.ISSUED),
                () -> assertThat(processed.getUserCouponId()).isNotNull(),
                () -> assertThat(processed.getProcessedAt()).isNotNull(),
                () -> assertThat(userCouponRepository.findIssuedCoupon(userId, template.getId())).isPresent(),
                () -> assertThat(found.getIssuedCount()).isEqualTo(1)
            );
        }

        @DisplayName("이미 발급받은 사용자면, 수량을 차감하지 않고 ALREADY_ISSUED 상태로 전이한다.")
        @Test
        void marksAlreadyIssued_whenUserAlreadyHasCoupon() {
            // arrange
            Long userId = 101L;
            CouponTemplate template = couponTemplateRepository.save(createTemplate(10, EXPIRED_AT));
            UserCoupon issued = userCouponRepository.save(template.issue(userId, REQUESTED_AT));
            CouponIssueRequest request = couponIssueRequestRepository.save(
                CouponIssueRequest.request(userId, template.getId(), REQUESTED_AT)
            );

            // act
            couponIssueRequestService.process(request.getId());

            // assert
            CouponIssueRequest processed = couponIssueRequestRepository.findById(request.getId()).orElseThrow();
            CouponTemplate found = couponTemplateRepository.findActiveById(template.getId()).orElseThrow();
            assertAll(
                () -> assertThat(processed.getStatus()).isEqualTo(CouponIssueRequestStatus.ALREADY_ISSUED),
                () -> assertThat(processed.getUserCouponId()).isEqualTo(issued.getId()),
                () -> assertThat(found.getIssuedCount()).isZero()
            );
        }

        @DisplayName("수량이 모두 소진되었으면, SOLD_OUT 상태로 전이하고 쿠폰을 발급하지 않는다.")
        @Test
        void marksSoldOut_whenQuantityIsExhausted() {
            // arrange
            CouponTemplate template = couponTemplateRepository.save(createTemplate(1, EXPIRED_AT));
            CouponIssueRequest first = couponIssueRequestRepository.save(
                CouponIssueRequest.request(101L, template.getId(), REQUESTED_AT)
            );
            CouponIssueRequest second = couponIssueRequestRepository.save(
                CouponIssueRequest.request(102L, template.getId(), REQUESTED_AT)
            );
            couponIssueRequestService.process(first.getId());

            // act
            couponIssueRequestService.process(second.getId());

            // assert
            CouponIssueRequest processed = couponIssueRequestRepository.findById(second.getId()).orElseThrow();
            assertAll(
                () -> assertThat(processed.getStatus()).isEqualTo(CouponIssueRequestStatus.SOLD_OUT),
                () -> assertThat(userCouponRepository.findIssuedCoupon(102L, template.getId())).isEmpty()
            );
        }

        @DisplayName("만료된 쿠폰이면, 수량을 차감하지 않고 FAILED 상태로 전이한다.")
        @Test
        void marksFailed_whenCouponIsExpired() {
            // arrange
            Long userId = 101L;
            CouponTemplate template = couponTemplateRepository.save(createTemplate(10, PAST_EXPIRED_AT));
            CouponIssueRequest request = couponIssueRequestRepository.save(
                CouponIssueRequest.request(userId, template.getId(), REQUESTED_AT)
            );

            // act
            couponIssueRequestService.process(request.getId());

            // assert
            CouponIssueRequest processed = couponIssueRequestRepository.findById(request.getId()).orElseThrow();
            CouponTemplate found = couponTemplateRepository.findActiveById(template.getId()).orElseThrow();
            assertAll(
                () -> assertThat(processed.getStatus()).isEqualTo(CouponIssueRequestStatus.FAILED),
                () -> assertThat(processed.getReason()).isNotBlank(),
                () -> assertThat(found.getIssuedCount()).isZero()
            );
        }

        @DisplayName("이미 처리된 요청이 재전달되면, 아무것도 바꾸지 않는다.")
        @Test
        void skipsProcessing_whenRequestIsAlreadyProcessed() {
            // arrange
            Long userId = 101L;
            CouponTemplate template = couponTemplateRepository.save(createTemplate(10, EXPIRED_AT));
            CouponIssueRequest request = couponIssueRequestRepository.save(
                CouponIssueRequest.request(userId, template.getId(), REQUESTED_AT)
            );
            couponIssueRequestService.process(request.getId());

            // act
            couponIssueRequestService.process(request.getId());

            // assert
            CouponTemplate found = couponTemplateRepository.findActiveById(template.getId()).orElseThrow();
            CouponIssueRequest processed = couponIssueRequestRepository.findById(request.getId()).orElseThrow();
            assertAll(
                () -> assertThat(processed.getStatus()).isEqualTo(CouponIssueRequestStatus.ISSUED),
                () -> assertThat(found.getIssuedCount()).isEqualTo(1)
            );
        }
    }

    @DisplayName("발급 요청을 동시에 처리할 때 ")
    @Nested
    class ProcessConcurrently {

        @DisplayName("총 수량보다 많은 요청이 동시에 처리되면, 정확히 총 수량만큼만 발급된다.")
        @Test
        void issuesExactlyTotalQuantity_whenConcurrentRequestsExceedQuantity() throws InterruptedException {
            // arrange
            int totalQuantity = 100;
            int concurrentRequests = 200;
            CouponTemplate template = couponTemplateRepository.save(createTemplate(totalQuantity, EXPIRED_AT));
            List<Long> requestIds = new ArrayList<>();
            for (long userId = 1; userId <= concurrentRequests; userId++) {
                requestIds.add(couponIssueRequestRepository.save(
                    CouponIssueRequest.request(userId, template.getId(), REQUESTED_AT)
                ).getId());
            }
            ExecutorService executor = Executors.newFixedThreadPool(20);
            CountDownLatch latch = new CountDownLatch(concurrentRequests);
            List<Throwable> failures = new CopyOnWriteArrayList<>();

            // act
            for (Long requestId : requestIds) {
                executor.submit(() -> {
                    try {
                        couponIssueRequestService.process(requestId);
                    } catch (Throwable t) {
                        failures.add(t);
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
            executor.shutdown();

            // assert
            CouponTemplate found = couponTemplateRepository.findActiveById(template.getId()).orElseThrow();
            long issuedCount = requestIds.stream()
                .map(id -> couponIssueRequestRepository.findById(id).orElseThrow())
                .filter(request -> request.getStatus() == CouponIssueRequestStatus.ISSUED)
                .count();
            long soldOutCount = requestIds.stream()
                .map(id -> couponIssueRequestRepository.findById(id).orElseThrow())
                .filter(request -> request.getStatus() == CouponIssueRequestStatus.SOLD_OUT)
                .count();
            assertAll(
                () -> assertThat(failures).isEmpty(),
                () -> assertThat(issuedCount).isEqualTo(totalQuantity),
                () -> assertThat(soldOutCount).isEqualTo(concurrentRequests - totalQuantity),
                () -> assertThat(found.getIssuedCount()).isEqualTo(totalQuantity)
            );
        }
    }

    private CouponTemplate createTemplate(Integer totalQuantity, ZonedDateTime expiredAt) {
        return CouponTemplate.create(
            COUPON_NAME,
            CouponType.FIXED,
            2_000L,
            10_000L,
            totalQuantity,
            expiredAt,
            FIXED_POLICY
        );
    }
}
