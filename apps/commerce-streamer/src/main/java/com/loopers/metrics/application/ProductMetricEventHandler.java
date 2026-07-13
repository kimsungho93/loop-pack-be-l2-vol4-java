package com.loopers.metrics.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@RequiredArgsConstructor
@Service
public class ProductMetricEventHandler {

    private final EventHandledRepository eventHandledRepository;
    private final ProductMetricsRepository productMetricsRepository;
    private final ProductMetricHourlyRepository productMetricHourlyRepository;

    @Transactional
    public void handle(CatalogEventEnvelope event, EventHandlingMetadata metadata) {
        ZonedDateTime handledAt = ZonedDateTime.now();
        boolean saved = eventHandledRepository.saveIfAbsent(event, metadata, handledAt);
        if (!saved) {
            return;
        }

        productMetricsRepository.add(ProductMetricDelta.from(event), handledAt);
        productMetricHourlyRepository.add(ProductMetricHourlyDelta.from(event), handledAt);
    }
}
