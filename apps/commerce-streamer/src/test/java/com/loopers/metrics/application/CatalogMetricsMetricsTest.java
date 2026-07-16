package com.loopers.metrics.application;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class CatalogMetricsMetricsTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final CatalogMetricsMetrics metrics = new CatalogMetricsMetrics(meterRegistry);

    @DisplayName("Catalog Metrics Batch 처리량과 지연을 기록한다.")
    @Test
    void recordsBatchThroughputAndDuration() {
        // act
        metrics.recordBatchRecords(3_000);
        metrics.recordAggregation(3_000, 2_700, 120, 350);
        metrics.recordBatchDuration(Duration.ofMillis(420).toNanos());
        metrics.recordBatchFailure();

        // assert
        assertAll(
            () -> assertThat(summaryTotal("catalog.metrics.batch.records")).isEqualTo(3_000),
            () -> assertThat(summaryTotal("catalog.metrics.batch.new.events")).isEqualTo(2_700),
            () -> assertThat(summaryTotal("catalog.metrics.batch.duplicate.events")).isEqualTo(300),
            () -> assertThat(summaryTotal("catalog.metrics.batch.product.groups")).isEqualTo(120),
            () -> assertThat(summaryTotal("catalog.metrics.batch.hourly.groups")).isEqualTo(350),
            () -> assertThat(meterRegistry.get("catalog.metrics.batch.processing.time")
                .timer()
                .totalTime(TimeUnit.MILLISECONDS)).isEqualTo(420),
            () -> assertThat(meterRegistry.get("catalog.metrics.batch.failures.total").counter().count())
                .isEqualTo(1)
        );
    }

    private double summaryTotal(String name) {
        return meterRegistry.get(name).summary().totalAmount();
    }
}
