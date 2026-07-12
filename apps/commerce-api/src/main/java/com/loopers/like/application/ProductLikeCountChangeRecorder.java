package com.loopers.like.application;

import com.loopers.like.domain.ProductLikeCountChange;
import com.loopers.like.domain.ProductLikeCountChangeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class ProductLikeCountChangeRecorder {

    private final ProductLikeCountChangeRepository productLikeCountChangeRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void increase(Long productId) {
        productLikeCountChangeRepository.save(ProductLikeCountChange.increase(productId));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void decrease(Long productId) {
        productLikeCountChangeRepository.save(ProductLikeCountChange.decrease(productId));
    }
}
