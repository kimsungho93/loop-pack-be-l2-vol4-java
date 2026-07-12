package com.loopers.metrics.application;

import java.time.ZonedDateTime;

public interface EventHandledRepository {

    boolean saveIfAbsent(CatalogEventEnvelope event, EventHandlingMetadata metadata, ZonedDateTime handledAt);
}
