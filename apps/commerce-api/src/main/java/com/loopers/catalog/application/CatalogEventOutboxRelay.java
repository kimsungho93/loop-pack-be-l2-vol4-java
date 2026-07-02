package com.loopers.catalog.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;

@RequiredArgsConstructor
@Component
public class CatalogEventOutboxRelay {

    private final CatalogEventOutboxRelayRepository catalogEventOutboxRelayRepository;
    private final CatalogEventPublisher catalogEventPublisher;
    private final CatalogEventOutboxRelayProperties properties;

    public int relay() {
        List<CatalogEventOutboxRelayItem> items = catalogEventOutboxRelayRepository.findPending(
            ZonedDateTime.now(),
            properties.chunkSize()
        );
        items.forEach(this::publish);
        return items.size();
    }

    private void publish(CatalogEventOutboxRelayItem item) {
        try {
            catalogEventPublisher.publish(item);
        } catch (RuntimeException e) {
            catalogEventOutboxRelayRepository.markPublishFailed(
                item.outboxId(),
                failureReason(e),
                properties.maxRetryCount(),
                ZonedDateTime.now()
            );
            return;
        }

        catalogEventOutboxRelayRepository.markPublished(item.outboxId(), ZonedDateTime.now());
    }

    private String failureReason(RuntimeException e) {
        if (e.getMessage() == null || e.getMessage().isBlank()) {
            return e.getClass().getSimpleName();
        }
        return e.getMessage();
    }
}
