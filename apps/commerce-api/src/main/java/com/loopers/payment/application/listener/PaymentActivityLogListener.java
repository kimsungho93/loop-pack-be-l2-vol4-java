package com.loopers.payment.application.listener;

import com.loopers.payment.application.event.OrderPaidEvent;
import com.loopers.payment.application.event.OrderPaymentFailedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class PaymentActivityLogListener {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void recordOrderPaid(OrderPaidEvent event) {
        log.info(
            "Order paid activity recorded. eventId={}, orderId={}, userId={}, paymentId={}, amount={}, pgTransactionKey={}",
            event.eventId(),
            event.orderId(),
            event.userId(),
            event.paymentId(),
            event.amount(),
            event.pgTransactionKey()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void recordOrderPaymentFailed(OrderPaymentFailedEvent event) {
        log.info(
            "Order payment failed activity recorded. eventId={}, orderId={}, userId={}, paymentId={}, amount={}, reason={}",
            event.eventId(),
            event.orderId(),
            event.userId(),
            event.paymentId(),
            event.amount(),
            event.failureReason()
        );
    }
}
