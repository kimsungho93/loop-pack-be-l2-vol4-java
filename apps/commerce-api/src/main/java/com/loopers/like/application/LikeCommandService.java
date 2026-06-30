package com.loopers.like.application;

import com.loopers.like.application.event.ProductLikeEventPublisher;
import com.loopers.like.domain.LikeChange;
import com.loopers.like.domain.LikeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@RequiredArgsConstructor
@Component
public class LikeCommandService {

    private final LikeService likeService;
    private final ProductLikeEventPublisher productLikeEventPublisher;

    @Transactional
    public void like(Long userId, Long productId) {
        LikeChange change = likeService.like(userId, productId);
        if (change.hasCountChange()) {
            productLikeEventPublisher.publishLiked(userId, productId, ZonedDateTime.now());
        }
    }

    @Transactional
    public void unlike(Long userId, Long productId) {
        LikeChange change = likeService.unlike(userId, productId);
        if (change.hasCountChange()) {
            productLikeEventPublisher.publishUnliked(userId, productId, ZonedDateTime.now());
        }
    }
}
