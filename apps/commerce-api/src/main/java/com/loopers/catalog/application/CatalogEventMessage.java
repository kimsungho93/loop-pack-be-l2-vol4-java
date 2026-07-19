package com.loopers.catalog.application;

import java.time.ZonedDateTime;
import java.util.Objects;

public record CatalogEventMessage(
    CatalogEventType eventType,
    Long productId,
    Long userId,
    Long brandId,
    Integer delta,
    Long orderId,
    Integer quantity,
    Long unitPrice,
    Long totalPrice,
    ZonedDateTime occurredAt
) {

    public CatalogEventMessage {
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(productId, "productId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    public static CatalogEventMessage productViewed(Long userId, Long productId, Long brandId, ZonedDateTime occurredAt) {
        return new CatalogEventMessage(
            CatalogEventType.PRODUCT_VIEWED,
            productId,
            userId,
            brandId,
            null,
            null,
            null,
            null,
            null,
            occurredAt
        );
    }

    public static CatalogEventMessage productLiked(Long userId, Long productId, ZonedDateTime occurredAt) {
        return new CatalogEventMessage(
            CatalogEventType.PRODUCT_LIKED,
            productId,
            userId,
            null,
            1,
            null,
            null,
            null,
            null,
            occurredAt
        );
    }

    public static CatalogEventMessage productUnliked(Long userId, Long productId, ZonedDateTime occurredAt) {
        return new CatalogEventMessage(
            CatalogEventType.PRODUCT_UNLIKED,
            productId,
            userId,
            null,
            -1,
            null,
            null,
            null,
            null,
            occurredAt
        );
    }

    public static CatalogEventMessage productOrdered(
        Long userId,
        Long orderId,
        Long productId,
        Integer quantity,
        Long unitPrice,
        Long totalPrice,
        ZonedDateTime occurredAt
    ) {
        return new CatalogEventMessage(
            CatalogEventType.PRODUCT_ORDERED,
            productId,
            userId,
            null,
            null,
            orderId,
            quantity,
            unitPrice,
            totalPrice,
            occurredAt
        );
    }

    public String partitionKey() {
        return String.valueOf(productId);
    }

    public CatalogEventPayload payload() {
        return new CatalogEventPayload(
            productId,
            userId,
            brandId,
            delta,
            orderId,
            quantity,
            unitPrice,
            totalPrice
        );
    }
}
