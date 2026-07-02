package com.loopers.coupon.application;

import com.loopers.coupon.domain.CouponIssueRequest;

import java.time.ZonedDateTime;

public record CouponIssueRequestMessage(
    Long requestId,
    Long userId,
    Long couponTemplateId,
    ZonedDateTime requestedAt
) {

    public static CouponIssueRequestMessage from(CouponIssueRequest request) {
        return new CouponIssueRequestMessage(
            request.getId(),
            request.getUserId(),
            request.getCouponTemplateId(),
            request.getRequestedAt()
        );
    }

    public String partitionKey() {
        return String.valueOf(couponTemplateId);
    }
}
