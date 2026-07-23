package com.loopers.ranking.application;

import com.loopers.shared.pagination.PageQuery;
import com.loopers.shared.pagination.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class RankingReadServiceTest {

    private static final LocalDate RANKING_DATE = LocalDate.of(2026, 7, 13);

    private FakeDailyRankingQuery dailyRankingQuery;
    private RankingReadService rankingReadService;

    @BeforeEach
    void setUp() {
        dailyRankingQuery = new FakeDailyRankingQuery();
        rankingReadService = new RankingReadService(dailyRankingQuery);
    }

    @DisplayName("일간 Ranking Page를 조회할 때")
    @Nested
    class GetDailyRanking {

        @DisplayName("0-based Page 범위를 조회하고 상품 위치를 1-based 순위로 변환한다")
        @Test
        void convertsPageRangeAndProductPositionsToRanks() {
            // arrange
            dailyRankingQuery.willReturn(new DailyRankingEntries(List.of(205L, 309L), 5));

            // act
            PageResult<RankingPosition> result = rankingReadService.getDailyRanking(
                RANKING_DATE,
                new PageQuery(1, 2)
            );

            // assert
            assertAll(
                () -> assertThat(dailyRankingQuery.date()).isEqualTo(RANKING_DATE),
                () -> assertThat(dailyRankingQuery.start()).isEqualTo(2),
                () -> assertThat(dailyRankingQuery.end()).isEqualTo(3),
                () -> assertThat(result.content()).containsExactly(
                    new RankingPosition(3, 205L),
                    new RankingPosition(4, 309L)
                ),
                () -> assertThat(result.totalElements()).isEqualTo(5),
                () -> assertThat(result.totalPages()).isEqualTo(3),
                () -> assertThat(result.number()).isEqualTo(1),
                () -> assertThat(result.size()).isEqualTo(2),
                () -> assertThat(result.first()).isFalse(),
                () -> assertThat(result.last()).isFalse()
            );
        }

        @DisplayName("Ranking이 비어 있으면 첫 페이지이자 마지막 페이지인 빈 결과를 반환한다")
        @Test
        void returnsEmptyFirstAndLastPage_whenRankingIsEmpty() {
            // arrange
            dailyRankingQuery.willReturn(new DailyRankingEntries(List.of(), 0));

            // act
            PageResult<RankingPosition> result = rankingReadService.getDailyRanking(
                RANKING_DATE,
                new PageQuery(0, 20)
            );

            // assert
            assertAll(
                () -> assertThat(result.content()).isEmpty(),
                () -> assertThat(result.totalElements()).isZero(),
                () -> assertThat(result.totalPages()).isZero(),
                () -> assertThat(result.first()).isTrue(),
                () -> assertThat(result.last()).isTrue()
            );
        }

        @DisplayName("마지막 범위를 조회하면 마지막 페이지로 표시한다")
        @Test
        void marksLastPage_whenLastRangeIsRequested() {
            // arrange
            dailyRankingQuery.willReturn(new DailyRankingEntries(List.of(401L), 5));

            // act
            PageResult<RankingPosition> result = rankingReadService.getDailyRanking(
                RANKING_DATE,
                new PageQuery(2, 2)
            );

            // assert
            assertAll(
                () -> assertThat(result.content()).containsExactly(new RankingPosition(5, 401L)),
                () -> assertThat(result.totalPages()).isEqualTo(3),
                () -> assertThat(result.last()).isTrue()
            );
        }
    }

    @DisplayName("상품의 일간 순위를 조회할 때")
    @Nested
    class GetDailyRank {

        @DisplayName("0-based Redis 위치를 1-based 순위로 변환한다")
        @Test
        void convertsRedisPositionToOneBasedRank() {
            // arrange
            dailyRankingQuery.willReturnRank(0L);

            // act
            Optional<Long> result = rankingReadService.getDailyRank(RANKING_DATE, 205L);

            // assert
            assertAll(
                () -> assertThat(dailyRankingQuery.rankDate()).isEqualTo(RANKING_DATE),
                () -> assertThat(dailyRankingQuery.productId()).isEqualTo(205L),
                () -> assertThat(result).contains(1L)
            );
        }

        @DisplayName("Ranking에 상품이 없으면 빈 순위를 반환한다")
        @Test
        void returnsEmpty_whenProductIsNotRanked() {
            // arrange
            dailyRankingQuery.willReturnEmptyRank();

            // act
            Optional<Long> result = rankingReadService.getDailyRank(RANKING_DATE, 205L);

            // assert
            assertThat(result).isEmpty();
        }
    }

    private static final class FakeDailyRankingQuery implements DailyRankingQuery {

        private DailyRankingEntries result;
        private LocalDate date;
        private long start;
        private long end;
        private Optional<Long> rank = Optional.empty();
        private LocalDate rankDate;
        private Long productId;

        @Override
        public DailyRankingEntries findDaily(LocalDate date, long start, long end) {
            this.date = date;
            this.start = start;
            this.end = end;
            return result;
        }

        @Override
        public Optional<Long> findDailyRank(LocalDate date, Long productId) {
            this.rankDate = date;
            this.productId = productId;
            return rank;
        }

        void willReturn(DailyRankingEntries result) {
            this.result = result;
        }

        void willReturnRank(long rank) {
            this.rank = Optional.of(rank);
        }

        void willReturnEmptyRank() {
            this.rank = Optional.empty();
        }

        LocalDate date() {
            return date;
        }

        long start() {
            return start;
        }

        long end() {
            return end;
        }

        LocalDate rankDate() {
            return rankDate;
        }

        Long productId() {
            return productId;
        }
    }
}
