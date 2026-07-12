package com.loopers.coupon.domain;

public enum CouponIssueRequestStatus {
    REQUESTED,
    ISSUED,
    SOLD_OUT,
    ALREADY_ISSUED,
    FAILED;

    public boolean isProcessed() {
        return this != REQUESTED;
    }
}
