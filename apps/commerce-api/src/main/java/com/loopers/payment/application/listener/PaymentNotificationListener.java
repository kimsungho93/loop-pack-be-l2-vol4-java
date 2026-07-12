package com.loopers.payment.application.listener;

import com.loopers.payment.application.event.OrderPaidEvent;
import com.loopers.payment.application.event.OrderPaymentFailedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class PaymentNotificationListener {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void notifyOrderPaid(OrderPaidEvent event) {
        log.info(
            "Order paid notification requested. eventId={}, orderId={}, userId={}, paymentId={}",
            event.eventId(),
            event.orderId(),
            event.userId(),
            event.paymentId()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void notifyOrderPaymentFailed(OrderPaymentFailedEvent event) {
        log.info(
            "Order payment failed notification requested. eventId={}, orderId={}, userId={}, paymentId={}, reason={}",
            event.eventId(),
            event.orderId(),
            event.userId(),
            event.paymentId(),
            event.failureReason()
        );
    }
}
