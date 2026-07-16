package com.loopers.metrics.application;

import java.time.ZonedDateTime;
import java.util.List;

public interface ProductMetricsRepository {

    void addAll(List<ProductMetricDelta> deltas, ZonedDateTime updatedAt);
}
