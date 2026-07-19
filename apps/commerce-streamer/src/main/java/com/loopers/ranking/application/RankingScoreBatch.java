package com.loopers.ranking.application;

import com.loopers.ranking.RankingWindow;

import java.util.List;
import java.util.Objects;

public record RankingScoreBatch(
    RankingWindow window,
    long productId,
    List<RankingScoreDelta> deltas
) {

    public RankingScoreBatch {
        Objects.requireNonNull(window, "window must not be null");
        Objects.requireNonNull(deltas, "deltas must not be null");
        if (deltas.isEmpty()) {
            throw new IllegalArgumentException("deltas must not be empty");
        }
        deltas = List.copyOf(deltas);
    }
}
