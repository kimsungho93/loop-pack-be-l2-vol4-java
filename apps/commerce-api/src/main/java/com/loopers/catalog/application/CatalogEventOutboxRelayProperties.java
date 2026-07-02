package com.loopers.catalog.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("commerce.catalog-event-outbox")
public record CatalogEventOutboxRelayProperties(
    @DefaultValue("1000") long relayDelayMs,
    @DefaultValue("100") int chunkSize,
    @DefaultValue("3") int maxRetryCount,
    @DefaultValue("3s") Duration sendTimeout
) {

    public CatalogEventOutboxRelayProperties {
        if (relayDelayMs <= 0) {
            throw new IllegalArgumentException("relayDelayMs must be positive.");
        }
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be positive.");
        }
        if (maxRetryCount <= 0) {
            throw new IllegalArgumentException("maxRetryCount must be positive.");
        }
        if (sendTimeout == null || sendTimeout.isZero() || sendTimeout.isNegative()) {
            throw new IllegalArgumentException("sendTimeout must be positive.");
        }
    }
}
