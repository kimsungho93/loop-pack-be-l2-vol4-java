package com.loopers.like.application.event;

import java.time.ZonedDateTime;

public record ProductUnlikedEvent(
    Long userId,
    Long productId,
    ZonedDateTime occurredAt
) {

    public static ProductUnlikedEvent occurred(Long userId, Long productId, ZonedDateTime occurredAt) {
        return new ProductUnlikedEvent(userId, productId, occurredAt);
    }
}
