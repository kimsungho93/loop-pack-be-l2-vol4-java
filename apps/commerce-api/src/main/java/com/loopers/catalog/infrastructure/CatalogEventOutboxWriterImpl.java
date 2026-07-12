package com.loopers.catalog.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.catalog.application.CatalogEventMessage;
import com.loopers.catalog.application.CatalogEventOutboxWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@RequiredArgsConstructor
@Component
public class CatalogEventOutboxWriterImpl implements CatalogEventOutboxWriter {

    private final CatalogEventOutboxJpaRepository catalogEventOutboxJpaRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void save(CatalogEventMessage message) {
        catalogEventOutboxJpaRepository.save(CatalogEventOutbox.pending(
            UUID.randomUUID().toString(),
            message.eventType(),
            message.productId(),
            message.partitionKey(),
            toPayloadJson(message),
            message.occurredAt()
        ));
    }

    private String toPayloadJson(CatalogEventMessage message) {
        try {
            return objectMapper.writeValueAsString(message.payload());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize catalog event payload", e);
        }
    }
}
