package com.loopers.ranking.application;

public interface RankingScoreRepository {

    long apply(RankingScoreBatch batch);
}
