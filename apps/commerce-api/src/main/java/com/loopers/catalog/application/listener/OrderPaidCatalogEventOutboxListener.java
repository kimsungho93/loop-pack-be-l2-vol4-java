package com.loopers.catalog.application.listener;

import com.loopers.catalog.application.CatalogEventMessage;
import com.loopers.catalog.application.CatalogEventOutboxWriter;
import com.loopers.payment.application.event.OrderPaidEvent;
import com.loopers.payment.application.event.OrderPaidItemSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@RequiredArgsConstructor
@Component
public class OrderPaidCatalogEventOutboxListener {

    private final CatalogEventOutboxWriter catalogEventOutboxWriter;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void recordProductOrdered(OrderPaidEvent event) {
        for (OrderPaidItemSnapshot item : event.items()) {
            catalogEventOutboxWriter.save(CatalogEventMessage.productOrdered(
                event.userId(),
                event.orderId(),
                item.productId(),
                item.quantity(),
                item.unitPrice(),
                item.totalPrice(),
                event.occurredAt()
            ));
        }
    }
}
