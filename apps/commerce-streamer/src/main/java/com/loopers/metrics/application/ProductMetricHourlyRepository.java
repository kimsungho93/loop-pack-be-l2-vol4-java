package com.loopers.metrics.application;

import java.time.ZonedDateTime;
import java.util.List;

public interface ProductMetricHourlyRepository {

    void addAll(List<ProductMetricHourlyDelta> deltas, ZonedDateTime updatedAt);
}
