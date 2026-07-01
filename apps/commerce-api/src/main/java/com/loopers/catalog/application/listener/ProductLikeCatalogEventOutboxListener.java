package com.loopers.catalog.application.listener;

import com.loopers.catalog.application.CatalogEventMessage;
import com.loopers.catalog.application.CatalogEventOutboxWriter;
import com.loopers.like.application.event.ProductLikedEvent;
import com.loopers.like.application.event.ProductUnlikedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@RequiredArgsConstructor
@Component
public class ProductLikeCatalogEventOutboxListener {

    private final CatalogEventOutboxWriter catalogEventOutboxWriter;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void recordProductLiked(ProductLikedEvent event) {
        catalogEventOutboxWriter.save(CatalogEventMessage.productLiked(
            event.userId(),
            event.productId(),
            event.occurredAt()
        ));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void recordProductUnliked(ProductUnlikedEvent event) {
        catalogEventOutboxWriter.save(CatalogEventMessage.productUnliked(
            event.userId(),
            event.productId(),
            event.occurredAt()
        ));
    }
}
