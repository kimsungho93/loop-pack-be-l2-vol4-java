package com.loopers.ranking.application;

import com.loopers.ranking.RankingScorePolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RankingRebuildServiceTest {

    private static final LocalDate RANKING_DATE = LocalDate.of(2026, 7, 13);

    @Mock
    private RankingRebuildMetricRepository metricRepository;

    private RankingRebuildService service;

    @BeforeEach
    void setUp() {
        RankingScorePolicy scorePolicy = new RankingScorePolicy(0.1, 0.2, 0.7, 10_000);
        service = new RankingRebuildService(metricRepository, scorePolicy);
    }

    @DisplayName("일간 Raw Metric을 실시간 적재와 동일한 Weight의 절대 Score로 계산한다")
    @Test
    void calculatesAbsoluteScores_fromDailyMetrics() {
        // arrange
        when(metricRepository.findAllByDate(RANKING_DATE)).thenReturn(List.of(
            new RankingRebuildMetric(101L, 5, 1, 35_000),
            new RankingRebuildMetric(202L, 2, -1, 10_000)
        ));

        // act
        List<RankingRebuildScore> scores = service.calculateScores(RANKING_DATE);

        // assert
        assertAll(
            () -> assertThat(scores).hasSize(2),
            () -> assertThat(scores.get(0).productId()).isEqualTo(101L),
            () -> assertThat(scores.get(0).score()).isCloseTo(3.15, offset(1.0e-10)),
            () -> assertThat(scores.get(1).productId()).isEqualTo(202L),
            () -> assertThat(scores.get(1).score()).isCloseTo(0.7, offset(1.0e-10))
        );
    }
}
