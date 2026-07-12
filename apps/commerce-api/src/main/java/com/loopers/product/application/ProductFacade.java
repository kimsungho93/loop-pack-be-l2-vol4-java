package com.loopers.product.application;

import com.loopers.product.application.event.ProductEventPublisher;
import com.loopers.product.domain.ProductSort;
import com.loopers.shared.error.CoreException;
import com.loopers.shared.error.ErrorType;
import com.loopers.shared.pagination.PageQuery;
import com.loopers.shared.pagination.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;

@RequiredArgsConstructor
@Component
public class ProductFacade {

    private final ProductListQuery productListQuery;
    private final ProductDetailQuery productDetailQuery;
    private final ProductEventPublisher productEventPublisher;

    public PageResult<ProductListInfo> getProducts(int page, int size, Long brandId, String sort) {
        return productListQuery.findVisibleProducts(
            new PageQuery(page, size),
            brandId,
            ProductSort.from(sort)
        );
    }

    public ProductDetailInfo getProduct(Long productId) {
        return getProduct(productId, null);
    }

    public ProductDetailInfo getProduct(Long productId, Long userId) {
        ProductDetailInfo info = productDetailQuery.findVisibleProduct(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품입니다."));
        productEventPublisher.publishViewed(userId, info, ZonedDateTime.now());
        return info;
    }
}
