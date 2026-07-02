package com.loopers.coupon.infrastructure;

import com.loopers.coupon.application.CouponIssueProperties;
import com.loopers.coupon.application.CouponIssueRequestMessage;
import com.loopers.coupon.application.CouponIssueRequestPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RequiredArgsConstructor
@Component
public class KafkaCouponIssueRequestPublisher implements CouponIssueRequestPublisher {

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final CouponIssueProperties properties;

    @Override
    public void publish(CouponIssueRequestMessage message) {
        try {
            kafkaTemplate.send(properties.topicName(), message.partitionKey(), message)
                .get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                "Interrupted while publishing coupon issue request. requestId=" + message.requestId(), e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException(
                "Failed to publish coupon issue request. requestId=" + message.requestId(), e);
        }
    }
}
