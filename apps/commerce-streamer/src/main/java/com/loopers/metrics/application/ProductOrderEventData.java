package com.loopers.metrics.application;

public record ProductOrderEventData(
    long productId,
    long orderId,
    int quantity,
    long unitPrice,
    long totalPrice
) {

    public static ProductOrderEventData from(CatalogEventEnvelope event) {
        CatalogEventPayload payload = event.payload();
        long productId = required(payload.productId(), "productId");
        long orderId = required(payload.orderId(), "orderId");
        int quantity = required(payload.quantity(), "quantity");
        long unitPrice = required(payload.unitPrice(), "unitPrice");
        long totalPrice = required(payload.totalPrice(), "totalPrice");

        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be at least 1");
        }
        if (unitPrice < 0) {
            throw new IllegalArgumentException("unitPrice must not be negative");
        }
        if (totalPrice < 0) {
            throw new IllegalArgumentException("totalPrice must not be negative");
        }
        if (unitPrice * quantity != totalPrice) {
            throw new IllegalArgumentException("totalPrice must equal unitPrice multiplied by quantity");
        }

        return new ProductOrderEventData(productId, orderId, quantity, unitPrice, totalPrice);
    }

    private static long required(Long value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return value;
    }

    private static int required(Integer value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return value;
    }
}
