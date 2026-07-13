package com.loopers.metrics.application;

import java.time.ZonedDateTime;

public interface ProductMetricHourlyRepository {

    void add(ProductMetricHourlyDelta delta, ZonedDateTime updatedAt);
}
