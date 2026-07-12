package com.loopers.like.application.event;

import java.time.ZonedDateTime;

public record ProductLikedEvent(
    Long userId,
    Long productId,
    ZonedDateTime occurredAt
) {

    public static ProductLikedEvent occurred(Long userId, Long productId, ZonedDateTime occurredAt) {
        return new ProductLikedEvent(userId, productId, occurredAt);
    }
}
