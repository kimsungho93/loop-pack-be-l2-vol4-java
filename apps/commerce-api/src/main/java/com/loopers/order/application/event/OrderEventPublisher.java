package com.loopers.order.application.event;

import com.loopers.order.application.OrderInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;

@RequiredArgsConstructor
@Component
public class OrderEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    public void publishCreated(OrderInfo order, ZonedDateTime occurredAt) {
        eventPublisher.publishEvent(OrderCreatedEvent.from(order, occurredAt));
    }
}
