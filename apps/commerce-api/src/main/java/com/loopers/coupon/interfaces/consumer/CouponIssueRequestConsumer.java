package com.loopers.coupon.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.coupon.application.CouponIssueRequestMessage;
import com.loopers.coupon.domain.CouponIssueRequestService;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.BatchListenerFailedException;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
@Component
public class CouponIssueRequestConsumer {

    private final CouponIssueRequestService couponIssueRequestService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "${commerce.coupon-issue.topic-name:coupon-issue-requests}",
        groupId = "${commerce.coupon-issue.group-id:coupon-issue-worker}",
        containerFactory = KafkaConfig.BATCH_LISTENER,
        autoStartup = "${commerce.coupon-issue.auto-startup:true}"
    )
    public void consume(
        List<ConsumerRecord<String, byte[]>> records,
        Acknowledgment acknowledgment
    ) {
        for (int index = 0; index < records.size(); index++) {
            ConsumerRecord<String, byte[]> record = records.get(index);
            try {
                couponIssueRequestService.process(read(record.value()).requestId());
            } catch (RuntimeException exception) {
                throw new BatchListenerFailedException("Failed to handle coupon issue request", exception, index);
            }
        }
        acknowledgment.acknowledge();
    }

    private CouponIssueRequestMessage read(byte[] value) {
        if (value == null) {
            throw new IllegalArgumentException("Coupon issue request value must not be null");
        }
        try {
            return objectMapper.readValue(value, CouponIssueRequestMessage.class);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Failed to read coupon issue request", exception);
        }
    }
}
