package com.loopers.coupon.application;

import com.loopers.coupon.domain.CouponIssueRequest;
import com.loopers.coupon.domain.CouponIssueRequestStatus;

import java.time.ZonedDateTime;

public record CouponIssueRequestInfo(
    Long requestId,
    CouponIssueRequestStatus status,
    Long userCouponId,
    String reason,
    ZonedDateTime requestedAt,
    ZonedDateTime processedAt
) {

    public static CouponIssueRequestInfo from(CouponIssueRequest request) {
        return new CouponIssueRequestInfo(
            request.getId(),
            request.getStatus(),
            request.getUserCouponId(),
            request.getReason(),
            request.getRequestedAt(),
            request.getProcessedAt()
        );
    }
}
