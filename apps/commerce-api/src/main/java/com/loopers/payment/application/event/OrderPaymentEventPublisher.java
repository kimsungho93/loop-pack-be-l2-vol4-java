package com.loopers.payment.application.event;

import com.loopers.order.domain.Order;
import com.loopers.payment.domain.Payment;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;

@RequiredArgsConstructor
@Component
public class OrderPaymentEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    public void publishPaid(Payment payment, Order order, ZonedDateTime occurredAt) {
        eventPublisher.publishEvent(OrderPaidEvent.from(payment, order, occurredAt));
    }

    public void publishFailed(Payment payment, ZonedDateTime occurredAt) {
        eventPublisher.publishEvent(OrderPaymentFailedEvent.from(payment, occurredAt));
    }
}
