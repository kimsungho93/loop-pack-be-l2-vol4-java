package com.loopers.ranking.application;

public record RankingScoreDelta(
    String eventId,
    double scoreDelta
) {

    public RankingScoreDelta {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId must not be blank");
        }
        if (!Double.isFinite(scoreDelta)) {
            throw new IllegalArgumentException("scoreDelta must be finite");
        }
    }
}
