package com.loopers.catalog.application;

import java.time.ZonedDateTime;
import java.util.Objects;

public record CatalogEventEnvelope(
    String eventId,
    CatalogEventType eventType,
    String aggregateType,
    Long aggregateId,
    CatalogEventPayload payload,
    ZonedDateTime occurredAt
) {

    public CatalogEventEnvelope {
        if (!hasText(eventId)) {
            throw new IllegalArgumentException("eventId must not be blank");
        }
        Objects.requireNonNull(eventType, "eventType must not be null");
        if (!hasText(aggregateType)) {
            throw new IllegalArgumentException("aggregateType must not be blank");
        }
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        Objects.requireNonNull(payload, "payload must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    public static CatalogEventEnvelope from(CatalogEventOutboxRelayItem item, CatalogEventPayload payload) {
        return new CatalogEventEnvelope(
            item.eventId(),
            item.eventType(),
            item.aggregateType(),
            item.aggregateId(),
            payload,
            item.occurredAt()
        );
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
