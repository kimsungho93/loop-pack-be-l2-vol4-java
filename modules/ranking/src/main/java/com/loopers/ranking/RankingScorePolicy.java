package com.loopers.ranking;

public final class RankingScorePolicy {

    private final double viewWeight;
    private final double likeWeight;
    private final double orderWeight;
    private final long orderAmountUnit;

    public RankingScorePolicy(double viewWeight, double likeWeight, double orderWeight, long orderAmountUnit) {
        this.viewWeight = viewWeight;
        this.likeWeight = likeWeight;
        this.orderWeight = orderWeight;
        this.orderAmountUnit = orderAmountUnit;
    }

    public double viewScore() {
        return viewWeight;
    }

    public double likeScore(long likeDelta) {
        return likeDelta * likeWeight;
    }

    public double orderScore(long orderAmount) {
        return ((double) orderAmount / orderAmountUnit) * orderWeight;
    }

    public double totalScore(long viewCount, long likeDelta, long orderAmount) {
        return viewCount * viewScore()
            + likeScore(likeDelta)
            + orderScore(orderAmount);
    }
}
