package com.loopers.catalog.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.catalog.application.CatalogEventEnvelope;
import com.loopers.catalog.application.CatalogEventOutboxRelayItem;
import com.loopers.catalog.application.CatalogEventOutboxRelayProperties;
import com.loopers.catalog.application.CatalogEventPayload;
import com.loopers.catalog.application.CatalogEventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaCatalogEventPublisherTest {

    private static final ZonedDateTime OCCURRED_AT = ZonedDateTime.parse("2026-07-02T10:00:00+09:00");

    @Mock
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @DisplayName("catalog 이벤트를 Kafka로 발행한다")
    @Nested
    class Publish {

        @DisplayName("설정된 topic에 productId를 key로 사용해 구조화된 이벤트 envelope을 발행한다")
        @Test
        void publishesCatalogEventEnvelopeWithProductIdKey() {
            // arrange
            CatalogEventOutboxRelayProperties properties = new CatalogEventOutboxRelayProperties(
                1_000L,
                100,
                3,
                Duration.ofSeconds(3),
                "catalog-events"
            );
            CatalogEventPayload payload = new CatalogEventPayload(101L, 1L, null, 1);
            KafkaCatalogEventPublisher publisher = new KafkaCatalogEventPublisher(
                kafkaTemplate,
                properties,
                new ObjectMapper()
            );
            CatalogEventOutboxRelayItem item = createItem();
            when(kafkaTemplate.send("catalog-events", "101", CatalogEventEnvelope.from(item, payload)))
                .thenReturn(CompletableFuture.completedFuture(null));

            // act
            publisher.publish(item);

            // assert
            ArgumentCaptor<CatalogEventEnvelope> envelopeCaptor = ArgumentCaptor.forClass(CatalogEventEnvelope.class);
            verify(kafkaTemplate).send(eq("catalog-events"), eq("101"), envelopeCaptor.capture());

            CatalogEventEnvelope envelope = envelopeCaptor.getValue();
            assertThat(envelope.eventId()).isEqualTo("event-1");
            assertThat(envelope.eventType()).isEqualTo(CatalogEventType.PRODUCT_LIKED);
            assertThat(envelope.aggregateType()).isEqualTo("PRODUCT");
            assertThat(envelope.aggregateId()).isEqualTo(101L);
            assertThat(envelope.payload()).isEqualTo(payload);
            assertThat(envelope.occurredAt()).isEqualTo(OCCURRED_AT);
        }
    }

    private CatalogEventOutboxRelayItem createItem() {
        return new CatalogEventOutboxRelayItem(
            1L,
            "event-1",
            CatalogEventType.PRODUCT_LIKED,
            "PRODUCT",
            101L,
            "101",
            "{\"productId\":101,\"userId\":1,\"delta\":1}",
            OCCURRED_AT
        );
    }
}
