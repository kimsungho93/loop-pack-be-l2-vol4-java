package com.loopers.metrics.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.metrics.application.CatalogEventEnvelope;
import com.loopers.metrics.application.EventHandlingMetadata;
import com.loopers.metrics.application.ProductMetricEventHandler;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.BatchListenerFailedException;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.io.IOException;
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
        // TODO: Aggregate unseen metric deltas by product/window and persist handled events and SOT in one batch transaction.
        for (int index = 0; index < records.size(); index++) {
            ConsumerRecord<String, byte[]> record = records.get(index);
            try {
                productMetricEventHandler.handle(
                    read(record.value()),
                    new EventHandlingMetadata(record.topic(), record.partition(), record.offset())
                );
            } catch (RuntimeException exception) {
                throw new BatchListenerFailedException("Failed to handle catalog event", exception, index);
            }
        }
        acknowledgment.acknowledge();
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
