package com.loopers.coupon.application;

public interface CouponIssueRequestPublisher {

    void publish(CouponIssueRequestMessage message);
}
