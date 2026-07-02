package com.loopers.metrics.application;

public record CatalogEventPayload(
    Long productId,
    Long userId,
    Long brandId,
    Integer delta
) {
}
