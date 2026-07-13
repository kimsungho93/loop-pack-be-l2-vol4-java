package com.loopers.payment.application.event;

import com.loopers.order.domain.Order;
import com.loopers.payment.domain.Payment;

import java.time.ZonedDateTime;
import java.util.List;

public record OrderPaidEvent(
    String eventId,
    Long orderId,
    Long paymentId,
    Long userId,
    long amount,
    String pgTransactionKey,
    List<OrderPaidItemSnapshot> items,
    ZonedDateTime occurredAt
) {

    public OrderPaidEvent {
        items = List.copyOf(items);
    }

    public static OrderPaidEvent from(Payment payment, Order order, ZonedDateTime occurredAt) {
        return new OrderPaidEvent(
            "payment:%s:paid".formatted(payment.getId()),
            payment.getOrderId(),
            payment.getId(),
            payment.getUserId(),
            payment.getAmount(),
            payment.getPgTransactionKey(),
            order.getItems().stream()
                .map(OrderPaidItemSnapshot::from)
                .toList(),
            occurredAt
        );
    }
}
