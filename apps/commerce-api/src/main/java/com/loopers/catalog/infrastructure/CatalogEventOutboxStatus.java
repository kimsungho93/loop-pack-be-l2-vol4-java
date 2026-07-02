package com.loopers.catalog.infrastructure;

public enum CatalogEventOutboxStatus {
    PENDING,
    PUBLISHED,
    RETRY_EXCEEDED
}
