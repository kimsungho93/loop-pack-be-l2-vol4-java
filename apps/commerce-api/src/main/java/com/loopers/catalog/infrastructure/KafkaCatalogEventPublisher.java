package com.loopers.catalog.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.catalog.application.CatalogEventEnvelope;
import com.loopers.catalog.application.CatalogEventOutboxRelayItem;
import com.loopers.catalog.application.CatalogEventOutboxRelayProperties;
import com.loopers.catalog.application.CatalogEventPayload;
import com.loopers.catalog.application.CatalogEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RequiredArgsConstructor
@Component
public class KafkaCatalogEventPublisher implements CatalogEventPublisher {

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final CatalogEventOutboxRelayProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public void publish(CatalogEventOutboxRelayItem item) {
        try {
            kafkaTemplate.send(
                    properties.topicName(),
                    item.partitionKey(),
                    CatalogEventEnvelope.from(item, toPayload(item))
                )
                .get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing catalog event. eventId=" + item.eventId(), e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Failed to publish catalog event. eventId=" + item.eventId(), e);
        }
    }

    private CatalogEventPayload toPayload(CatalogEventOutboxRelayItem item) {
        try {
            return objectMapper.readValue(item.payload(), CatalogEventPayload.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize catalog event payload. eventId=" + item.eventId(), e);
        }
    }
}
