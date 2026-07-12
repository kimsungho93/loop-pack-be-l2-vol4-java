package com.loopers.coupon.application;

import java.time.ZonedDateTime;

public interface CouponIssueGate {

    CouponIssueGateResult tryPass(Long userId, Long couponTemplateId, int totalQuantity, ZonedDateTime expiredAt);
}
