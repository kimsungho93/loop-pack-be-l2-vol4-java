package com.loopers.metrics.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.metrics.application.CatalogEventEnvelope;
import com.loopers.metrics.application.EventHandlingMetadata;
import com.loopers.metrics.application.ProductMetricEventHandler;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
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
        records.forEach(record -> productMetricEventHandler.handle(
            read(record.value()),
            new EventHandlingMetadata(record.topic(), record.partition(), record.offset())
        ));
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
