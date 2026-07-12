package com.loopers.payment.application.event;

import com.loopers.payment.domain.Payment;
import com.loopers.payment.domain.PaymentFailureReason;

import java.time.ZonedDateTime;

public record OrderPaymentFailedEvent(
    String eventId,
    Long orderId,
    Long paymentId,
    Long userId,
    long amount,
    String pgTransactionKey,
    PaymentFailureReason failureReason,
    String pgReason,
    ZonedDateTime occurredAt
) {

    public static OrderPaymentFailedEvent from(Payment payment, ZonedDateTime occurredAt) {
        return new OrderPaymentFailedEvent(
            "payment:%s:failed".formatted(payment.getId()),
            payment.getOrderId(),
            payment.getId(),
            payment.getUserId(),
            payment.getAmount(),
            payment.getPgTransactionKey(),
            payment.getFailureReason(),
            payment.getPgReason(),
            occurredAt
        );
    }
}
