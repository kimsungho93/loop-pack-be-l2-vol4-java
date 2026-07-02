package com.loopers.catalog.infrastructure;

import com.loopers.catalog.application.CatalogEventOutboxRelay;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@ConditionalOnProperty(
    name = "commerce.catalog-event-outbox.relay-enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class CatalogEventOutboxRelayScheduler {

    private final CatalogEventOutboxRelay catalogEventOutboxRelay;

    @Scheduled(fixedDelayString = "${commerce.catalog-event-outbox.relay-delay-ms:1000}")
    public void relay() {
        catalogEventOutboxRelay.relay();
    }
}
