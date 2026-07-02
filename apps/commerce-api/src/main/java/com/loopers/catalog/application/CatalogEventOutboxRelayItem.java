package com.loopers.catalog.application;

import java.time.ZonedDateTime;

public record CatalogEventOutboxRelayItem(
    Long outboxId,
    String eventId,
    CatalogEventType eventType,
    String aggregateType,
    Long aggregateId,
    String partitionKey,
    String payload,
    ZonedDateTime occurredAt
) {
}
