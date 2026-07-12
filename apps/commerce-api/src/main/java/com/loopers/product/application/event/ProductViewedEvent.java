package com.loopers.product.application.event;

import com.loopers.product.application.ProductDetailInfo;

import java.time.ZonedDateTime;

public record ProductViewedEvent(
    Long userId,
    Long productId,
    Long brandId,
    ZonedDateTime occurredAt
) {

    public static ProductViewedEvent from(Long userId, ProductDetailInfo product, ZonedDateTime occurredAt) {
        return new ProductViewedEvent(
            userId,
            product.id(),
            product.brand().id(),
            occurredAt
        );
    }
}
