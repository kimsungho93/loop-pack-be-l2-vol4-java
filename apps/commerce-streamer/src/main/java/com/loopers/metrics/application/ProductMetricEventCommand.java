package com.loopers.metrics.application;

import java.util.Objects;

public record ProductMetricEventCommand(
    CatalogEventEnvelope event,
    EventHandlingMetadata metadata
) {

    public ProductMetricEventCommand {
        Objects.requireNonNull(event, "event must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
    }
}
