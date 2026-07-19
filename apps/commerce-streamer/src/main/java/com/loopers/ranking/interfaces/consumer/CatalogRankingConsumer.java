package com.loopers.ranking.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.metrics.application.CatalogEventEnvelope;
import com.loopers.ranking.application.RankingScoreEventHandler;
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
public class CatalogRankingConsumer {

    private final RankingScoreEventHandler rankingScoreEventHandler;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "${commerce.metrics.catalog.topic-name:catalog-events}",
        groupId = "${commerce.ranking.catalog.group-id:catalog-ranking}",
        containerFactory = KafkaConfig.BATCH_LISTENER,
        autoStartup = "${commerce.ranking.catalog.auto-startup:true}"
    )
    public void consume(
        List<ConsumerRecord<String, byte[]>> records,
        Acknowledgment acknowledgment
    ) {
        List<CatalogEventEnvelope> events = new ArrayList<>(records.size());
        for (int index = 0; index < records.size(); index++) {
            try {
                events.add(read(records.get(index).value()));
            } catch (RuntimeException exception) {
                handle(events);
                throw new BatchListenerFailedException(
                    "Failed to read catalog ranking event",
                    exception,
                    index
                );
            }
        }

        handle(events);
        acknowledgment.acknowledge();
    }

    private void handle(List<CatalogEventEnvelope> events) {
        if (events.isEmpty()) {
            return;
        }
        try {
            rankingScoreEventHandler.handle(List.copyOf(events));
        } catch (RuntimeException exception) {
            throw new BatchListenerFailedException(
                "Failed to handle catalog ranking event batch",
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
