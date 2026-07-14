package com.loopers.ranking.application;

public record RankingRebuildMetric(
    Long productId,
    long viewCount,
    long likeDelta,
    long orderAmount
) {
}
