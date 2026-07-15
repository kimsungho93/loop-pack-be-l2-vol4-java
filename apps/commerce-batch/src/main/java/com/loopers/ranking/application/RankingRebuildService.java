package com.loopers.ranking.application;

import com.loopers.ranking.RankingScorePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@RequiredArgsConstructor
@Service
public class RankingRebuildService {

    private final RankingRebuildMetricRepository metricRepository;
    private final RankingScorePolicy scorePolicy;

    public List<RankingRebuildScore> calculateScores(LocalDate rankingDate) {
        return metricRepository.findAllByDate(rankingDate).stream()
            .map(this::calculateScore)
            .toList();
    }

    private RankingRebuildScore calculateScore(RankingRebuildMetric metric) {
        double score = scorePolicy.totalScore(
            metric.viewCount(),
            metric.likeDelta(),
            metric.orderAmount()
        );
        return new RankingRebuildScore(metric.productId(), score);
    }
}
