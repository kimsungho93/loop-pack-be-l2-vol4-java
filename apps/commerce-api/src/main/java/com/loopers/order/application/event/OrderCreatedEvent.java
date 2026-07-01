package com.loopers.order.application.event;

import com.loopers.order.application.OrderInfo;

import java.time.ZonedDateTime;

public record OrderCreatedEvent(
    Long orderId,
    Long userId,
    long orderTotalPrice,
    long paymentAmount,
    int itemCount,
    ZonedDateTime occurredAt
) {

    public static OrderCreatedEvent from(OrderInfo order, ZonedDateTime occurredAt) {
        return new OrderCreatedEvent(
            order.id(),
            order.userId(),
            order.orderTotalPrice(),
            order.paymentAmount(),
            order.items().size(),
            occurredAt
        );
    }
}
