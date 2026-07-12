package com.loopers.product.application.listener;

import com.loopers.product.application.event.ProductViewedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ProductActivityLogListener {

    @EventListener
    public void recordProductViewed(ProductViewedEvent event) {
        log.info(
            "Product viewed activity recorded. userId={}, productId={}, brandId={}",
            event.userId(),
            event.productId(),
            event.brandId()
        );
    }
}
