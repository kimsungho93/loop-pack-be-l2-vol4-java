package com.loopers.coupon.infrastructure;

import com.loopers.coupon.domain.CouponTemplate;
import com.loopers.coupon.domain.CouponTemplateRepository;
import com.loopers.coupon.domain.CouponType;
import com.loopers.coupon.domain.policy.FixedCouponDiscountPolicy;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class CouponTemplateRepositoryIntegrationTest {

    private static final String COUPON_NAME = "1주년 쿠폰";
    private static final ZonedDateTime EXPIRED_AT = ZonedDateTime.parse("2026-12-31T23:59:59+09:00");
    private static final FixedCouponDiscountPolicy FIXED_POLICY = new FixedCouponDiscountPolicy();

    private final CouponTemplateRepository couponTemplateRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    CouponTemplateRepositoryIntegrationTest(
        CouponTemplateRepository couponTemplateRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.couponTemplateRepository = couponTemplateRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("발급 수량을 차감할 때 ")
    @Nested
    class IncreaseIssuedCount {

        @DisplayName("총 수량 안에서는 성공하고, 총 수량에 도달하면 실패한다.")
        @Test
        void increasesUntilTotalQuantity_thenFails() {
            // arrange
            CouponTemplate template = couponTemplateRepository.save(createTemplate(2));

            // act
            boolean first = couponTemplateRepository.increaseIssuedCount(template.getId());
            boolean second = couponTemplateRepository.increaseIssuedCount(template.getId());
            boolean third = couponTemplateRepository.increaseIssuedCount(template.getId());

            // assert
            CouponTemplate found = couponTemplateRepository.findActiveById(template.getId()).orElseThrow();
            assertAll(
                () -> assertThat(first).isTrue(),
                () -> assertThat(second).isTrue(),
                () -> assertThat(third).isFalse(),
                () -> assertThat(found.getIssuedCount()).isEqualTo(2)
            );
        }

        @DisplayName("총 수량이 없는 무제한 쿠폰이면, 항상 성공한다.")
        @Test
        void alwaysIncreases_whenTotalQuantityIsNull() {
            // arrange
            CouponTemplate template = couponTemplateRepository.save(createTemplate(null));

            // act
            boolean first = couponTemplateRepository.increaseIssuedCount(template.getId());
            boolean second = couponTemplateRepository.increaseIssuedCount(template.getId());

            // assert
            CouponTemplate found = couponTemplateRepository.findActiveById(template.getId()).orElseThrow();
            assertAll(
                () -> assertThat(first).isTrue(),
                () -> assertThat(second).isTrue(),
                () -> assertThat(found.getIssuedCount()).isEqualTo(2)
            );
        }

        @DisplayName("삭제된 쿠폰이면, 실패한다.")
        @Test
        void fails_whenTemplateIsDeleted() {
            // arrange
            CouponTemplate template = createTemplate(2);
            template.delete();
            couponTemplateRepository.save(template);

            // act
            boolean result = couponTemplateRepository.increaseIssuedCount(template.getId());

            // assert
            assertThat(result).isFalse();
        }
    }

    private CouponTemplate createTemplate(Integer totalQuantity) {
        return CouponTemplate.create(
            COUPON_NAME,
            CouponType.FIXED,
            2_000L,
            10_000L,
            totalQuantity,
            EXPIRED_AT,
            FIXED_POLICY
        );
    }
}
