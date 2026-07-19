package com.loopers.metrics.application;

import java.util.Objects;

public record ProductMetricEventCommand(
    CatalogEventEnvelope event,
    EventHandlingMetadata metadata,
    ProductMetricDelta metricDelta,
    ProductMetricHourlyDelta hourlyDelta
) {

    public ProductMetricEventCommand(
        CatalogEventEnvelope event,
        EventHandlingMetadata metadata
    ) {
        this(
            Objects.requireNonNull(event, "event must not be null"),
            Objects.requireNonNull(metadata, "metadata must not be null"),
            ProductMetricDelta.from(event),
            ProductMetricHourlyDelta.from(event)
        );
    }

    public ProductMetricEventCommand {
        Objects.requireNonNull(event, "event must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
        Objects.requireNonNull(metricDelta, "metricDelta must not be null");
        Objects.requireNonNull(hourlyDelta, "hourlyDelta must not be null");
    }
}
