package com.loopers.coupon.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("commerce.coupon-issue")
public record CouponIssueProperties(
    @DefaultValue("coupon-issue-requests") String topicName,
    @DefaultValue("3s") Duration sendTimeout
) {

    public CouponIssueProperties {
        if (topicName == null || topicName.isBlank()) {
            throw new IllegalArgumentException("topicName must not be blank.");
        }
        if (sendTimeout == null || sendTimeout.isZero() || sendTimeout.isNegative()) {
            throw new IllegalArgumentException("sendTimeout must be positive.");
        }
    }
}
