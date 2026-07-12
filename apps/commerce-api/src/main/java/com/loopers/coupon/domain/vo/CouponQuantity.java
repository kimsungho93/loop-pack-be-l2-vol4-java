package com.loopers.coupon.domain.vo;

import com.loopers.shared.error.CoreException;
import com.loopers.shared.error.ErrorType;
import jakarta.persistence.Embeddable;

@Embeddable
public record CouponQuantity(int value) {

    public CouponQuantity {
        if (value <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 수량은 0보다 커야 합니다.");
        }
    }

    public static CouponQuantity of(int value) {
        return new CouponQuantity(value);
    }
}
