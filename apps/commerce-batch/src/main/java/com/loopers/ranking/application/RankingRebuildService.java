package com.loopers.ranking.application;

import com.loopers.ranking.RankingScorePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@RequiredArgsConstructor
@Service
public class RankingRebuildService {

    private final RankingRebuildMetricRepository metricRepository;
    private final RankingScorePolicy scorePolicy;
    private final RankingColdStartProperties coldStartProperties;

    public List<RankingRebuildScore> calculateScores(LocalDate rankingDate) {
        Map<Long, AccumulatedScore> scoresByProduct = new TreeMap<>();
        metricRepository.findAllThrough(rankingDate).stream()
            .sorted(Comparator.comparing(RankingRebuildMetric::date))
            .forEach(metric -> scoresByProduct.compute(
                metric.productId(),
                (productId, previous) -> accumulate(previous, metric)
            ));

        return scoresByProduct.entrySet().stream()
            .map(entry -> new RankingRebuildScore(
                entry.getKey(),
                carry(entry.getValue(), rankingDate)
            ))
            .toList();
    }

    private AccumulatedScore accumulate(AccumulatedScore previous, RankingRebuildMetric metric) {
        double rawScore = scorePolicy.totalScore(
            metric.viewCount(),
            metric.likeDelta(),
            metric.orderAmount()
        );
        double carriedScore = previous == null ? 0 : carry(previous, metric.date());
        return new AccumulatedScore(metric.date(), carriedScore + rawScore);
    }

    private double carry(AccumulatedScore score, LocalDate targetDate) {
        long elapsedDays = ChronoUnit.DAYS.between(score.date(), targetDate);
        return score.value() * Math.pow(coldStartProperties.carryOverRate(), elapsedDays);
    }

    private record AccumulatedScore(
        LocalDate date,
        double value
    ) {
    }
}
