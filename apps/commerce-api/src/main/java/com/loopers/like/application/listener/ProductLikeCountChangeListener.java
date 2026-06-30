package com.loopers.like.application.listener;

import com.loopers.like.application.ProductLikeCountChangeRecorder;
import com.loopers.like.application.event.ProductLikedEvent;
import com.loopers.like.application.event.ProductUnlikedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@RequiredArgsConstructor
@Component
public class ProductLikeCountChangeListener {

    private final ProductLikeCountChangeRecorder productLikeCountChangeRecorder;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void recordIncrease(ProductLikedEvent event) {
        try {
            productLikeCountChangeRecorder.increase(event.productId());
        } catch (RuntimeException e) {
            log.warn(
                "Failed to record product like increase. userId={}, productId={}",
                event.userId(),
                event.productId(),
                e
            );
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void recordDecrease(ProductUnlikedEvent event) {
        try {
            productLikeCountChangeRecorder.decrease(event.productId());
        } catch (RuntimeException e) {
            log.warn(
                "Failed to record product like decrease. userId={}, productId={}",
                event.userId(),
                event.productId(),
                e
            );
        }
    }
}
