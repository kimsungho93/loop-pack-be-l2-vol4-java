package com.loopers.ranking.application;

import com.loopers.ranking.RankingPeriod;

import java.util.List;

public interface ProductRankingCandidateRepository {

    void upsertAll(
        RankingPeriod period,
        List<? extends RankingCandidate> candidates
    );
}
