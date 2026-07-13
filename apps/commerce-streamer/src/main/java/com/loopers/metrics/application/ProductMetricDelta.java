package com.loopers.metrics.application;

import java.util.Objects;

public record ProductMetricDelta(
    Long productId,
    long likeCountDelta,
    long viewCountDelta,
    long salesCountDelta
) {

    public ProductMetricDelta {
        Objects.requireNonNull(productId, "productId must not be null");
    }

    public static ProductMetricDelta from(CatalogEventEnvelope event) {
        Long productId = event.payload().productId();

        return switch (event.eventType()) {
            case PRODUCT_VIEWED -> new ProductMetricDelta(productId, 0, 1, 0);
            case PRODUCT_LIKED, PRODUCT_UNLIKED -> new ProductMetricDelta(productId, requiredDelta(event), 0, 0);
            case PRODUCT_ORDERED -> {
                ProductOrderEventData order = ProductOrderEventData.from(event);
                yield new ProductMetricDelta(order.productId(), 0, 0, order.quantity());
            }
        };
    }

    private static long requiredDelta(CatalogEventEnvelope event) {
        Integer delta = event.payload().delta();
        if (delta == null) {
            throw new IllegalArgumentException("delta must not be null for like metric event");
        }
        return delta;
    }
}
