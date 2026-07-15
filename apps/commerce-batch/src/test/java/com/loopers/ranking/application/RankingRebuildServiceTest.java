package com.loopers.ranking.application;

import com.loopers.ranking.RankingScorePolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RankingRebuildServiceTest {

    private static final LocalDate RANKING_DATE = LocalDate.of(2026, 7, 13);
    private static final long RUN_ID = 42L;

    @Mock
    private RankingRebuildMetricRepository metricRepository;

    @Mock
    private RankingRebuildRepository rebuildRepository;

    @Captor
    private ArgumentCaptor<List<RankingRebuildScore>> scoresCaptor;

    private RankingRebuildService service;

    @BeforeEach
    void setUp() {
        RankingScorePolicy scorePolicy = new RankingScorePolicy(0.1, 0.2, 0.7, 10_000);
        RankingColdStartProperties coldStartProperties = new RankingColdStartProperties(0.1);
        service = new RankingRebuildService(metricRepository, rebuildRepository, scorePolicy, coldStartProperties);
    }

    @DisplayName("계산한 절대 Score로 대상 날짜의 Redis 랭킹을 교체한다")
    @Test
    void rebuildsRankingWithCalculatedScores() {
        // arrange
        when(metricRepository.findAllThrough(RANKING_DATE)).thenReturn(List.of(
            new RankingRebuildMetric(RANKING_DATE, 101L, 5, 1, 35_000),
            new RankingRebuildMetric(RANKING_DATE, 202L, 2, -1, 10_000)
        ));
        when(rebuildRepository.replace(eq(RANKING_DATE), eq(RUN_ID), anyList())).thenReturn(2L);

        // act
        long replacedCount = service.rebuild(RANKING_DATE, RUN_ID);

        // assert
        verify(rebuildRepository).replace(eq(RANKING_DATE), eq(RUN_ID), scoresCaptor.capture());
        List<RankingRebuildScore> scores = scoresCaptor.getValue();
        assertAll(
            () -> assertThat(replacedCount).isEqualTo(2),
            () -> assertThat(scores).hasSize(2),
            () -> assertThat(scores.get(0).productId()).isEqualTo(101L),
            () -> assertThat(scores.get(0).score()).isCloseTo(3.15, offset(1.0e-10)),
            () -> assertThat(scores.get(1).productId()).isEqualTo(202L),
            () -> assertThat(scores.get(1).score()).isCloseTo(0.7, offset(1.0e-10))
        );
    }

    @DisplayName("Redis 랭킹 교체에 실패하면 예외를 상위로 전파한다")
    @Test
    void propagatesFailure_whenReplacingRankingFails() {
        // arrange
        when(metricRepository.findAllThrough(RANKING_DATE)).thenReturn(List.of());
        when(rebuildRepository.replace(eq(RANKING_DATE), eq(RUN_ID), anyList()))
            .thenThrow(new IllegalStateException("Redis unavailable"));

        // act & assert
        assertThatThrownBy(() -> service.rebuild(RANKING_DATE, RUN_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Redis unavailable");
    }

    @DisplayName("일간 Raw Metric을 실시간 적재와 동일한 Weight의 절대 Score로 계산한다")
    @Test
    void calculatesAbsoluteScores_fromDailyMetrics() {
        // arrange
        when(metricRepository.findAllThrough(RANKING_DATE)).thenReturn(List.of(
            new RankingRebuildMetric(RANKING_DATE, 101L, 5, 1, 35_000),
            new RankingRebuildMetric(RANKING_DATE, 202L, 2, -1, 10_000)
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

    @DisplayName("최초 SOT 날짜부터 대상 날짜까지 Raw Score와 Carry-Over를 순서대로 누적한다")
    @Test
    void replaysRawScoresAndCarryOverThroughRankingDate() {
        // arrange
        LocalDate targetDate = RANKING_DATE.plusDays(1);
        when(metricRepository.findAllThrough(targetDate)).thenReturn(List.of(
            new RankingRebuildMetric(targetDate, 101L, 10, 0, 0),
            new RankingRebuildMetric(RANKING_DATE.minusDays(1), 101L, 100, 0, 0),
            new RankingRebuildMetric(RANKING_DATE, 101L, 20, 0, 0)
        ));

        // act
        List<RankingRebuildScore> scores = service.calculateScores(targetDate);

        // assert
        assertAll(
            () -> assertThat(scores).hasSize(1),
            () -> assertThat(scores.getFirst().productId()).isEqualTo(101L),
            () -> assertThat(scores.getFirst().score()).isCloseTo(1.3, offset(1.0e-10))
        );
    }

    @DisplayName("이벤트가 없는 날짜도 Carry-Over 비율만큼 감쇠하여 대상 날짜 Score를 재생한다")
    @Test
    void replaysCarryOverThroughDatesWithoutMetrics() {
        // arrange
        LocalDate targetDate = RANKING_DATE.plusDays(1);
        when(metricRepository.findAllThrough(targetDate)).thenReturn(List.of(
            new RankingRebuildMetric(targetDate, 101L, 10, 0, 0),
            new RankingRebuildMetric(RANKING_DATE.minusDays(1), 101L, 100, 0, 0)
        ));

        // act
        List<RankingRebuildScore> scores = service.calculateScores(targetDate);

        // assert
        assertAll(
            () -> assertThat(scores).hasSize(1),
            () -> assertThat(scores.getFirst().productId()).isEqualTo(101L),
            () -> assertThat(scores.getFirst().score()).isCloseTo(1.1, offset(1.0e-10))
        );
    }
}
