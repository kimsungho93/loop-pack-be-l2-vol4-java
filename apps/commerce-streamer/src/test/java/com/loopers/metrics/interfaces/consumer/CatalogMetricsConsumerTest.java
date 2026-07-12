package com.loopers.metrics.interfaces.consumer;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.loopers.metrics.application.CatalogEventEnvelope;
import com.loopers.metrics.application.CatalogEventPayload;
import com.loopers.metrics.application.CatalogEventType;
import com.loopers.metrics.application.EventHandlingMetadata;
import com.loopers.metrics.application.ProductMetricEventHandler;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CatalogMetricsConsumerTest {

    private static final ZonedDateTime OCCURRED_AT = ZonedDateTime.parse("2026-07-02T10:00:00+09:00");

    @Mock
    private ProductMetricEventHandler productMetricEventHandler;

    @Mock
    private Acknowledgment acknowledgment;

    private CatalogMetricsConsumer consumer;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        consumer = new CatalogMetricsConsumer(productMetricEventHandler, objectMapper);
    }

    @DisplayName("catalog metrics 이벤트를 소비할 때")
    @Nested
    class Consume {

        @DisplayName("배치의 모든 이벤트를 처리한 뒤 ack 한다.")
        @Test
        void acknowledgesAfterHandlingAllRecords() throws IOException {
            // arrange
            CatalogEventEnvelope event = event("event-1");
            ConsumerRecord<String, byte[]> record = record(event, 1, 20L);

            // act
            consumer.consume(List.of(record), acknowledgment);

            // assert
            ArgumentCaptor<CatalogEventEnvelope> eventCaptor = ArgumentCaptor.forClass(CatalogEventEnvelope.class);
            ArgumentCaptor<EventHandlingMetadata> metadataCaptor = ArgumentCaptor.forClass(EventHandlingMetadata.class);
            verify(productMetricEventHandler).handle(eventCaptor.capture(), metadataCaptor.capture());
            CatalogEventEnvelope actual = eventCaptor.getValue();
            assertThat(actual.eventId()).isEqualTo(event.eventId());
            assertThat(actual.eventType()).isEqualTo(event.eventType());
            assertThat(actual.aggregateType()).isEqualTo(event.aggregateType());
            assertThat(actual.aggregateId()).isEqualTo(event.aggregateId());
            assertThat(actual.payload()).isEqualTo(event.payload());
            assertThat(actual.occurredAt().toInstant()).isEqualTo(event.occurredAt().toInstant());
            assertThat(metadataCaptor.getValue()).isEqualTo(new EventHandlingMetadata("catalog-events", 1, 20L));
            verify(acknowledgment).acknowledge();
        }

        @DisplayName("처리 중 실패하면 ack 하지 않는다.")
        @Test
        void doesNotAcknowledge_whenHandlingFails() throws IOException {
            // arrange
            ConsumerRecord<String, byte[]> record = record(event("event-1"), 1, 20L);
            doThrow(new RuntimeException("db unavailable"))
                .when(productMetricEventHandler)
                .handle(any(CatalogEventEnvelope.class), any(EventHandlingMetadata.class));

            // act & assert
            assertThatThrownBy(() -> consumer.consume(List.of(record), acknowledgment))
                .isInstanceOf(BatchListenerFailedException.class)
                .hasCauseInstanceOf(RuntimeException.class)
                .hasRootCauseMessage("db unavailable");

            verify(acknowledgment, never()).acknowledge();
        }

        @DisplayName("배치 중간의 이벤트를 읽을 수 없으면 실패한 레코드 index를 전달한다.")
        @Test
        void throwsBatchListenerFailedException_whenRecordCannotBeRead() throws IOException {
            // arrange
            ConsumerRecord<String, byte[]> first = record(event("event-1"), 1, 20L);
            ConsumerRecord<String, byte[]> second = invalidRecord(1, 21L);

            // act & assert
            assertThatThrownBy(() -> consumer.consume(List.of(first, second), acknowledgment))
                .isInstanceOfSatisfying(BatchListenerFailedException.class, exception ->
                    assertThat(exception.getIndex()).isEqualTo(1)
                )
                .hasCauseInstanceOf(IllegalArgumentException.class);

            verify(productMetricEventHandler).handle(any(CatalogEventEnvelope.class), any(EventHandlingMetadata.class));
            verify(acknowledgment, never()).acknowledge();
        }
    }

    private ConsumerRecord<String, byte[]> record(
        CatalogEventEnvelope event,
        int partition,
        long offset
    ) throws IOException {
        return new ConsumerRecord<>("catalog-events", partition, offset, "101", objectMapper.writeValueAsBytes(event));
    }

    private ConsumerRecord<String, byte[]> invalidRecord(
        int partition,
        long offset
    ) {
        return new ConsumerRecord<>("catalog-events", partition, offset, "101", "{invalid-json".getBytes(StandardCharsets.UTF_8));
    }

    private CatalogEventEnvelope event(String eventId) {
        return new CatalogEventEnvelope(
            eventId,
            CatalogEventType.PRODUCT_LIKED,
            "PRODUCT",
            101L,
            new CatalogEventPayload(101L, 1L, null, 1),
            OCCURRED_AT
        );
    }
}
