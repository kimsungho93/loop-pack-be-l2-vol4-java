package com.loopers.metrics.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.metrics.application.CatalogEventEnvelope;
import com.loopers.metrics.application.EventHandlingMetadata;
import com.loopers.metrics.application.ProductMetricEventCommand;
import com.loopers.metrics.application.ProductMetricEventHandler;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.BatchListenerFailedException;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Component
public class CatalogMetricsConsumer {

    private final ProductMetricEventHandler productMetricEventHandler;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "${commerce.metrics.catalog.topic-name:catalog-events}",
        groupId = "${commerce.metrics.catalog.group-id:catalog-metrics}",
        containerFactory = KafkaConfig.BATCH_LISTENER,
        autoStartup = "${commerce.metrics.catalog.auto-startup:true}"
    )
    public void consume(
        List<ConsumerRecord<String, byte[]>> records,
        Acknowledgment acknowledgment
    ) {
        List<ProductMetricEventCommand> commands = new ArrayList<>(records.size());
        for (int index = 0; index < records.size(); index++) {
            ConsumerRecord<String, byte[]> record = records.get(index);
            try {
                commands.add(command(record));
            } catch (RuntimeException exception) {
                handle(commands);
                throw new BatchListenerFailedException(
                    "Failed to read catalog metric event",
                    exception,
                    index
                );
            }
        }

        handle(commands);
        acknowledgment.acknowledge();
    }

    private ProductMetricEventCommand command(ConsumerRecord<String, byte[]> record) {
        return new ProductMetricEventCommand(
            read(record.value()),
            new EventHandlingMetadata(record.topic(), record.partition(), record.offset())
        );
    }

    private void handle(List<ProductMetricEventCommand> commands) {
        if (commands.isEmpty()) {
            return;
        }
        try {
            productMetricEventHandler.handleBatch(List.copyOf(commands));
        } catch (RuntimeException exception) {
            throw new BatchListenerFailedException(
                "Failed to handle catalog metric event batch",
                exception,
                0
            );
        }
    }

    private CatalogEventEnvelope read(byte[] value) {
        if (value == null) {
            throw new IllegalArgumentException("Catalog event value must not be null");
        }
        try {
            return objectMapper.readValue(value, CatalogEventEnvelope.class);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Failed to read catalog event", exception);
        }
    }
}
