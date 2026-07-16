package com.loopers.metrics.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Service
public class ProductMetricEventHandler {

    private final EventHandledRepository eventHandledRepository;
    private final ProductMetricsRepository productMetricsRepository;
    private final ProductMetricHourlyRepository productMetricHourlyRepository;
    private final CatalogMetricsMetrics catalogMetricsMetrics;

    @Transactional
    public void handle(CatalogEventEnvelope event, EventHandlingMetadata metadata) {
        handleCommands(List.of(new ProductMetricEventCommand(event, metadata)));
    }

    @Transactional
    public void handleBatch(List<ProductMetricEventCommand> commands) {
        handleCommands(commands);
    }

    private void handleCommands(List<ProductMetricEventCommand> commands) {
        ZonedDateTime handledAt = ZonedDateTime.now();
        Map<Long, ProductMetricDelta> metricDeltas = new LinkedHashMap<>();
        Map<ProductMetricHourlyGroup, ProductMetricHourlyDelta> hourlyDeltas = new LinkedHashMap<>();
        int newEventCount = 0;

        for (ProductMetricEventCommand command : commands) {
            boolean saved = eventHandledRepository.saveIfAbsent(
                command.event(),
                command.metadata(),
                handledAt
            );
            if (!saved) {
                continue;
            }
            newEventCount++;

            ProductMetricDelta metricDelta = command.metricDelta();
            metricDeltas.merge(metricDelta.productId(), metricDelta, ProductMetricDelta::plus);

            ProductMetricHourlyDelta hourlyDelta = command.hourlyDelta();
            ProductMetricHourlyGroup hourlyGroup = new ProductMetricHourlyGroup(
                hourlyDelta.windowStart(),
                hourlyDelta.productId()
            );
            hourlyDeltas.merge(hourlyGroup, hourlyDelta, ProductMetricHourlyDelta::plus);
        }

        if (!metricDeltas.isEmpty()) {
            productMetricsRepository.addAll(List.copyOf(metricDeltas.values()), handledAt);
        }
        if (!hourlyDeltas.isEmpty()) {
            productMetricHourlyRepository.addAll(List.copyOf(hourlyDeltas.values()), handledAt);
        }
        catalogMetricsMetrics.recordAggregation(
            commands.size(),
            newEventCount,
            metricDeltas.size(),
            hourlyDeltas.size()
        );
    }

    private record ProductMetricHourlyGroup(
        LocalDateTime windowStart,
        long productId
    ) {
    }
}
