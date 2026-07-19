package com.loopers.metrics.application;

public record CatalogEventPayload(
    Long productId,
    Long userId,
    Long brandId,
    Integer delta,
    Long orderId,
    Integer quantity,
    Long unitPrice,
    Long totalPrice
) {

    public CatalogEventPayload(Long productId, Long userId, Long brandId, Integer delta) {
        this(productId, userId, brandId, delta, null, null, null, null);
    }
}
