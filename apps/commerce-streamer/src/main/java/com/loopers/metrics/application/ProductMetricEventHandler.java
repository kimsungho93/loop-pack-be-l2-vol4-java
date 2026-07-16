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

        for (ProductMetricEventCommand command : commands) {
            boolean saved = eventHandledRepository.saveIfAbsent(
                command.event(),
                command.metadata(),
                handledAt
            );
            if (!saved) {
                continue;
            }

            ProductMetricDelta metricDelta = command.metricDelta();
            metricDeltas.merge(metricDelta.productId(), metricDelta, ProductMetricDelta::plus);

            ProductMetricHourlyDelta hourlyDelta = command.hourlyDelta();
            ProductMetricHourlyGroup hourlyGroup = new ProductMetricHourlyGroup(
                hourlyDelta.windowStart(),
                hourlyDelta.productId()
            );
            hourlyDeltas.merge(hourlyGroup, hourlyDelta, ProductMetricHourlyDelta::plus);
        }

        metricDeltas.values().forEach(delta -> productMetricsRepository.add(delta, handledAt));
        hourlyDeltas.values().forEach(delta -> productMetricHourlyRepository.add(delta, handledAt));
    }

    private record ProductMetricHourlyGroup(
        LocalDateTime windowStart,
        long productId
    ) {
    }
}
