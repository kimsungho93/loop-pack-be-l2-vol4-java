package com.loopers.coupon.interfaces.consumer;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.loopers.coupon.application.CouponIssueRequestMessage;
import com.loopers.coupon.domain.CouponIssueRequestService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.listener.BatchListenerFailedException;
import org.springframework.kafka.support.Acknowledgment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CouponIssueRequestConsumerTest {

    private static final ZonedDateTime REQUESTED_AT = ZonedDateTime.parse("2026-07-03T10:00:00+09:00");

    @Mock
    private CouponIssueRequestService couponIssueRequestService;

    @Mock
    private Acknowledgment acknowledgment;

    private CouponIssueRequestConsumer consumer;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        consumer = new CouponIssueRequestConsumer(couponIssueRequestService, objectMapper);
    }

    @DisplayName("발급 요청 메시지를 소비할 때")
    @Nested
    class Consume {

        @DisplayName("배치의 모든 요청을 처리한 뒤 ack 한다.")
        @Test
        void acknowledgesAfterProcessingAllRecords() throws IOException {
            // arrange
            ConsumerRecord<String, byte[]> first = record(1001L, 20L);
            ConsumerRecord<String, byte[]> second = record(1002L, 21L);

            // act
            consumer.consume(List.of(first, second), acknowledgment);

            // assert
            verify(couponIssueRequestService).process(1001L);
            verify(couponIssueRequestService).process(1002L);
            verify(acknowledgment).acknowledge();
        }

        @DisplayName("처리 중 실패하면, 실패한 레코드 index를 담아 던지고 ack 하지 않는다.")
        @Test
        void doesNotAcknowledge_whenProcessingFails() throws IOException {
            // arrange
            ConsumerRecord<String, byte[]> first = record(1001L, 20L);
            ConsumerRecord<String, byte[]> second = record(1002L, 21L);
            doNothing()
                .doThrow(new RuntimeException("db unavailable"))
                .when(couponIssueRequestService)
                .process(anyLong());

            // act & assert
            assertThatThrownBy(() -> consumer.consume(List.of(first, second), acknowledgment))
                .hasRootCauseMessage("db unavailable")
                .isInstanceOfSatisfying(BatchListenerFailedException.class, exception ->
                    assertThat(exception.getIndex()).isEqualTo(1)
                );

            verify(acknowledgment, never()).acknowledge();
        }

        @DisplayName("메시지를 읽을 수 없으면, 처리 없이 실패한 레코드 index를 전달한다.")
        @Test
        void throwsBatchListenerFailedException_whenRecordCannotBeRead() {
            // arrange
            ConsumerRecord<String, byte[]> invalid = new ConsumerRecord<>(
                "coupon-issue-requests", 0, 20L, "11", "{invalid-json".getBytes(StandardCharsets.UTF_8)
            );

            // act & assert
            assertThatThrownBy(() -> consumer.consume(List.of(invalid), acknowledgment))
                .isInstanceOfSatisfying(BatchListenerFailedException.class, exception ->
                    assertThat(exception.getIndex()).isZero()
                )
                .hasCauseInstanceOf(IllegalArgumentException.class);

            verify(couponIssueRequestService, never()).process(anyLong());
            verify(acknowledgment, never()).acknowledge();
        }
    }

    private ConsumerRecord<String, byte[]> record(Long requestId, long offset) throws IOException {
        CouponIssueRequestMessage message = new CouponIssueRequestMessage(requestId, 101L, 11L, REQUESTED_AT);
        return new ConsumerRecord<>("coupon-issue-requests", 0, offset, "11", objectMapper.writeValueAsBytes(message));
    }
}
