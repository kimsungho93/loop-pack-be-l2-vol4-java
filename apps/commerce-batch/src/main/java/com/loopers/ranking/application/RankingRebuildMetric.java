package com.loopers.ranking.application;

import java.time.LocalDate;

public record RankingRebuildMetric(
    LocalDate date,
    Long productId,
    long viewCount,
    long likeDelta,
    long orderAmount
) {
}
