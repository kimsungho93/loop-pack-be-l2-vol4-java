package com.loopers.catalog.application;

public interface CatalogEventOutboxWriter {

    void save(CatalogEventMessage message);
}
