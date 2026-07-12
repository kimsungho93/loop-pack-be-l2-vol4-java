package com.loopers.payment.application.event;

import com.loopers.payment.domain.Payment;

import java.time.ZonedDateTime;

public record OrderPaidEvent(
    String eventId,
    Long orderId,
    Long paymentId,
    Long userId,
    long amount,
    String pgTransactionKey,
    ZonedDateTime occurredAt
) {

    public static OrderPaidEvent from(Payment payment, ZonedDateTime occurredAt) {
        return new OrderPaidEvent(
            "payment:%s:paid".formatted(payment.getId()),
            payment.getOrderId(),
            payment.getId(),
            payment.getUserId(),
            payment.getAmount(),
            payment.getPgTransactionKey(),
            occurredAt
        );
    }
}
