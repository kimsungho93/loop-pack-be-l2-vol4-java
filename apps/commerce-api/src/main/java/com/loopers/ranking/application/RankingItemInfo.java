package com.loopers.ranking.application;

import com.loopers.product.application.ProductListInfo;

public record RankingItemInfo(
    long rank,
    ProductListInfo product
) {
}
