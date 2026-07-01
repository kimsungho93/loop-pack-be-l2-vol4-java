package com.loopers.catalog.application;

public record CatalogEventPayload(
    Long productId,
    Long userId,
    Long brandId,
    Integer delta
) {
}
