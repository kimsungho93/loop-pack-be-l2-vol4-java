package com.loopers.like.application.listener;

import com.loopers.like.application.event.ProductLikedEvent;
import com.loopers.like.application.event.ProductUnlikedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class LikeActivityLogListener {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void recordProductLiked(ProductLikedEvent event) {
        log.info(
            "Product liked activity recorded. userId={}, productId={}",
            event.userId(),
            event.productId()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void recordProductUnliked(ProductUnlikedEvent event) {
        log.info(
            "Product unliked activity recorded. userId={}, productId={}",
            event.userId(),
            event.productId()
        );
    }
}
