package com.loopers.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class RankingRedisKeyTest {

    private static final LocalDate RANKING_DATE = LocalDate.of(2026, 7, 13);

    @DisplayName("일간 Ranking Key에 이벤트 날짜 Hash Tag를 사용한다.")
    @Test
    void createsDailyKey() {
        // act & assert
        assertThat(RankingRedisKey.daily(RANKING_DATE)).isEqualTo("ranking:all:{20260713}");
    }

    @DisplayName("시간 Ranking Key에 이벤트 날짜 Hash Tag와 2자리 시간을 사용한다.")
    @Test
    void createsHourlyKey() {
        // arrange
        RankingWindow window = new RankingWindow(RANKING_DATE, 3);

        // act & assert
        assertThat(RankingRedisKey.hourly(window)).isEqualTo("ranking:hourly:{20260713}:03");
    }

    @DisplayName("중복 처리 Key에 이벤트 날짜 Hash Tag를 사용한다.")
    @Test
    void createsHandledKey() {
        // act & assert
        assertThat(RankingRedisKey.handled(RANKING_DATE)).isEqualTo("ranking:handled:{20260713}");
    }

    @DisplayName("Carry-Over 임시 Key와 대상 Key가 같은 날짜 Hash Tag를 사용한다.")
    @Test
    void createsCarryOverTempKey() {
        // act & assert
        assertThat(RankingRedisKey.carryOverTemp(RANKING_DATE))
            .isEqualTo("ranking:all:{20260713}:carry-over:temp");
    }

    @DisplayName("Rebuild 임시 Key와 대상 Key가 같은 날짜 Hash Tag를 사용한다.")
    @Test
    void createsRebuildTempKey() {
        // act & assert
        assertThat(RankingRedisKey.rebuildTemp(RANKING_DATE, 42L))
            .isEqualTo("ranking:rebuild:{20260713}:42");
    }
}
