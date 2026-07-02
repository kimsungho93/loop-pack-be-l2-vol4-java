package com.loopers.catalog.application;

public interface CatalogEventPublisher {

    void publish(CatalogEventOutboxRelayItem item);
}
