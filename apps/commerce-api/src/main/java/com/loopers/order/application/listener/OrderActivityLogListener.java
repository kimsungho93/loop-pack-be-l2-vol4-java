package com.loopers.order.application.listener;

import com.loopers.order.application.event.OrderCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class OrderActivityLogListener {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void recordOrderCreated(OrderCreatedEvent event) {
        log.info(
            "Order created activity recorded. orderId={}, userId={}, orderTotalPrice={}, paymentAmount={}, itemCount={}",
            event.orderId(),
            event.userId(),
            event.orderTotalPrice(),
            event.paymentAmount(),
            event.itemCount()
        );
    }
}
