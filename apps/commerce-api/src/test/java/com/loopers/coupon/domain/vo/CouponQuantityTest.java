package com.loopers.coupon.domain.vo;

import com.loopers.shared.error.CoreException;
import com.loopers.shared.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CouponQuantityTest {

    @DisplayName("쿠폰 수량이 0 이하이면, BAD_REQUEST 예외를 던진다.")
    @Test
    void throwsBadRequest_whenQuantityIsNotPositive() {
        // arrange
        int quantity = 0;

        // act & assert
        assertThatThrownBy(() -> CouponQuantity.of(quantity))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("쿠폰 수량이 양수이면, 정상 생성된다.")
    @Test
    void createsQuantity_whenValueIsPositive() {
        // arrange
        int quantity = 100;

        // act
        CouponQuantity couponQuantity = CouponQuantity.of(quantity);

        // assert
        assertThat(couponQuantity.value()).isEqualTo(100);
    }
}
