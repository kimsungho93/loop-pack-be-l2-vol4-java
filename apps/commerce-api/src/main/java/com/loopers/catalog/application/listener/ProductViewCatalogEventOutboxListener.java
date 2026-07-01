package com.loopers.catalog.application.listener;

import com.loopers.catalog.application.CatalogEventMessage;
import com.loopers.catalog.application.CatalogEventOutboxWriter;
import com.loopers.product.application.event.ProductViewedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class ProductViewCatalogEventOutboxListener {

    private final CatalogEventOutboxWriter catalogEventOutboxWriter;

    @EventListener
    public void recordProductViewed(ProductViewedEvent event) {
        CatalogEventMessage message = CatalogEventMessage.productViewed(
            event.userId(),
            event.productId(),
            event.brandId(),
            event.occurredAt()
        );

        try {
            catalogEventOutboxWriter.save(message);
        } catch (RuntimeException e) {
            log.warn(
                "Failed to record product view catalog event. userId={}, productId={}",
                event.userId(),
                event.productId(),
                e
            );
        }
    }
}
