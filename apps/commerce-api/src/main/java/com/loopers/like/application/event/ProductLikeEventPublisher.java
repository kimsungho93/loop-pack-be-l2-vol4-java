package com.loopers.like.application.event;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;

@RequiredArgsConstructor
@Component
public class ProductLikeEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    public void publishLiked(Long userId, Long productId, ZonedDateTime occurredAt) {
        eventPublisher.publishEvent(ProductLikedEvent.occurred(userId, productId, occurredAt));
    }

    public void publishUnliked(Long userId, Long productId, ZonedDateTime occurredAt) {
        eventPublisher.publishEvent(ProductUnlikedEvent.occurred(userId, productId, occurredAt));
    }
}
