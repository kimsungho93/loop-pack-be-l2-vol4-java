package com.loopers.metrics.application;

import java.time.ZonedDateTime;

public interface ProductMetricsRepository {

    void add(ProductMetricDelta delta, ZonedDateTime updatedAt);
}
