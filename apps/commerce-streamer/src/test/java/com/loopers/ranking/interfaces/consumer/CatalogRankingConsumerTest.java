package com.loopers.ranking.interfaces.consumer;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.loopers.metrics.application.CatalogEventEnvelope;
import com.loopers.metrics.application.CatalogEventPayload;
import com.loopers.metrics.application.CatalogEventType;
import com.loopers.ranking.application.RankingScoreEventHandler;
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
class CatalogRankingConsumerTest {

    private static final ZonedDateTime OCCURRED_AT = ZonedDateTime.parse("2026-07-13T10:00:00+09:00");

    @Mock
    private RankingScoreEventHandler rankingScoreEventHandler;

    @Mock
    private Acknowledgment acknowledgment;

    private CatalogRankingConsumer consumer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        consumer = new CatalogRankingConsumer(rankingScoreEventHandler, objectMapper);
    }

    @DisplayName("Catalog Ranking 이벤트 Batch를 소비할 때")
    @Nested
    class Consume {

        @DisplayName("모든 이벤트를 하나의 Batch로 전달한 뒤 Ack한다.")
        @Test
        void acknowledgesAfterHandlingAllEventsAsOneBatch() throws IOException {
            // arrange
            CatalogEventEnvelope first = event("event-1", CatalogEventType.PRODUCT_VIEWED, null);
            CatalogEventEnvelope second = event("event-2", CatalogEventType.PRODUCT_LIKED, 1);

            // act
            consumer.consume(
                List.of(record(first, 10L), record(second, 11L)),
                acknowledgment
            );

            // assert
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<CatalogEventEnvelope>> captor = ArgumentCaptor.forClass(List.class);
            verify(rankingScoreEventHandler).handle(captor.capture());
            assertThat(captor.getValue())
                .extracting(CatalogEventEnvelope::eventId)
                .containsExactly("event-1", "event-2");
            verify(acknowledgment).acknowledge();
        }

        @DisplayName("읽을 수 없는 Record가 있으면 해당 index를 전달하고 Ack하지 않는다.")
        @Test
        void handlesValidPrefixAndReportsIndexWhenRecordCannotBeRead() throws IOException {
            // arrange
            ConsumerRecord<String, byte[]> first = record(
                event("event-1", CatalogEventType.PRODUCT_VIEWED, null),
                10L
            );
            ConsumerRecord<String, byte[]> second = invalidRecord(11L);

            // act & assert
            assertThatThrownBy(() -> consumer.consume(List.of(first, second), acknowledgment))
                .isInstanceOfSatisfying(BatchListenerFailedException.class, exception ->
                    assertThat(exception.getIndex()).isEqualTo(1)
                )
                .hasCauseInstanceOf(IllegalArgumentException.class);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<CatalogEventEnvelope>> captor = ArgumentCaptor.forClass(List.class);
            verify(rankingScoreEventHandler).handle(captor.capture());
            assertThat(captor.getValue())
                .extracting(CatalogEventEnvelope::eventId)
                .containsExactly("event-1");
            verify(acknowledgment, never()).acknowledge();
        }

        @DisplayName("Ranking 반영에 실패하면 Batch 처음부터 재처리하도록 전달하고 Ack하지 않는다.")
        @Test
        void doesNotAcknowledgeAndRetriesWholeBatchWhenHandlingFails() throws IOException {
            // arrange
            ConsumerRecord<String, byte[]> record = record(
                event("event-1", CatalogEventType.PRODUCT_VIEWED, null),
                10L
            );
            doThrow(new IllegalStateException("redis unavailable"))
                .when(rankingScoreEventHandler)
                .handle(any());

            // act & assert
            assertThatThrownBy(() -> consumer.consume(List.of(record), acknowledgment))
                .isInstanceOfSatisfying(BatchListenerFailedException.class, exception ->
                    assertThat(exception.getIndex()).isZero()
                )
                .hasRootCauseMessage("redis unavailable");

            verify(acknowledgment, never()).acknowledge();
        }
    }

    private ConsumerRecord<String, byte[]> record(CatalogEventEnvelope event, long offset) throws IOException {
        return new ConsumerRecord<>(
            "catalog-events",
            0,
            offset,
            "101",
            objectMapper.writeValueAsBytes(event)
        );
    }

    private ConsumerRecord<String, byte[]> invalidRecord(long offset) {
        return new ConsumerRecord<>(
            "catalog-events",
            0,
            offset,
            "101",
            "{invalid-json".getBytes(StandardCharsets.UTF_8)
        );
    }

    private CatalogEventEnvelope event(String eventId, CatalogEventType eventType, Integer delta) {
        return new CatalogEventEnvelope(
            eventId,
            eventType,
            "PRODUCT",
            101L,
            new CatalogEventPayload(101L, 1L, null, delta),
            OCCURRED_AT
        );
    }
}
