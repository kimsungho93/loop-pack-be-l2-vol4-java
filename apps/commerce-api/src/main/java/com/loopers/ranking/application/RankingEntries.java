package com.loopers.ranking.application;

import java.util.List;

public record RankingEntries(
    List<Long> productIds,
    long totalElements
) {

    public RankingEntries {
        productIds = List.copyOf(productIds);
    }
}
