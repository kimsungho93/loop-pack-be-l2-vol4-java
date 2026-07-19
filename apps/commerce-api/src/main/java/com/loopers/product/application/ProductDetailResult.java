package com.loopers.product.application;

public record ProductDetailResult(
    ProductDetailInfo product,
    Long rank
) {
}
