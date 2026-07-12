package com.loopers.product.application.event;

import com.loopers.product.application.ProductDetailInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;

@RequiredArgsConstructor
@Component
public class ProductEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    public void publishViewed(Long userId, ProductDetailInfo product, ZonedDateTime occurredAt) {
        eventPublisher.publishEvent(ProductViewedEvent.from(userId, product, occurredAt));
    }
}
