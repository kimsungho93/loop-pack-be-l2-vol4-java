package com.loopers.ranking.application;

import com.loopers.ranking.RankingPeriod;

import java.util.List;

public interface ProductRankingCandidateRepository {

    void upsertAll(
        RankingPeriod period,
        List<? extends RankingCandidate> candidates
    );

    long countCandidates(RankingPeriod period, long snapshotId);

    List<RankingCandidate> findTopCandidates(
        RankingPeriod period,
        long snapshotId,
        int limit
    );

    void assignRanks(
        RankingPeriod period,
        long snapshotId,
        List<ProductRankingAssignment> assignments
    );

    List<ProductRankingAssignment> findRankedProducts(
        RankingPeriod period,
        long snapshotId,
        int limit
    );
}
