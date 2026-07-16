package com.loopers.metrics.application;

import com.loopers.ranking.RankingWindow;

import java.time.LocalDateTime;
import java.util.Objects;

public record ProductMetricHourlyDelta(
    LocalDateTime windowStart,
    Long productId,
    long viewCountDelta,
    long likeDelta,
    long orderQuantityDelta,
    long orderAmountDelta
) {

    public ProductMetricHourlyDelta {
        Objects.requireNonNull(windowStart, "windowStart must not be null");
        Objects.requireNonNull(productId, "productId must not be null");
    }

    public static ProductMetricHourlyDelta from(CatalogEventEnvelope event) {
        RankingWindow window = RankingWindow.from(event.occurredAt());
        if (event.eventType() == CatalogEventType.PRODUCT_ORDERED) {
            ProductOrderEventData order = ProductOrderEventData.from(event);
            return new ProductMetricHourlyDelta(
                window.date().atTime(window.hour(), 0),
                order.productId(),
                0,
                0,
                order.quantity(),
                order.totalPrice()
            );
        }

        ProductMetricDelta metricDelta = ProductMetricDelta.from(event);

        return new ProductMetricHourlyDelta(
            window.date().atTime(window.hour(), 0),
            metricDelta.productId(),
            metricDelta.viewCountDelta(),
            metricDelta.likeCountDelta(),
            0,
            0
        );
    }

    public ProductMetricHourlyDelta plus(ProductMetricHourlyDelta other) {
        if (!productId.equals(other.productId())) {
            throw new IllegalArgumentException("productId must match to add hourly metric deltas");
        }
        if (!windowStart.equals(other.windowStart())) {
            throw new IllegalArgumentException("windowStart must match to add hourly metric deltas");
        }

        return new ProductMetricHourlyDelta(
            windowStart,
            productId,
            viewCountDelta + other.viewCountDelta(),
            likeDelta + other.likeDelta(),
            orderQuantityDelta + other.orderQuantityDelta(),
            orderAmountDelta + other.orderAmountDelta()
        );
    }
}
