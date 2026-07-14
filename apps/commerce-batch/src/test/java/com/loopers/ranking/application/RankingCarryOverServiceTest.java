package com.loopers.ranking.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RankingCarryOverServiceTest {

    private static final LocalDate SOURCE_DATE = LocalDate.of(2026, 7, 13);
    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 7, 14);
    private static final double CARRY_OVER_RATE = 0.1;
    private static final Clock BEFORE_TARGET_DATE = Clock.fixed(
        Instant.parse("2026-07-13T14:50:00Z"),
        ZoneOffset.UTC
    );
    private static final Clock TARGET_DATE_STARTED = Clock.fixed(
        Instant.parse("2026-07-13T15:00:00Z"),
        ZoneOffset.UTC
    );

    @Mock
    private RankingCarryOverRepository repository;

    @DisplayName("일간 Ranking을 다음 날짜로 이월할 때")
    @Nested
    class CarryOver {

        @DisplayName("Target 시작 전이면 전날 Ranking에 설정 비율을 적용해 이월한다")
        @Test
        void carriesOverPreviousDateWithConfiguredRate_beforeTargetStarts() {
            // arrange
            RankingColdStartProperties properties = new RankingColdStartProperties(CARRY_OVER_RATE);
            RankingCarryOverService service = new RankingCarryOverService(
                repository,
                properties,
                BEFORE_TARGET_DATE
            );
            when(repository.carryOver(SOURCE_DATE, TARGET_DATE, CARRY_OVER_RATE)).thenReturn(2L);

            // act
            long carriedCount = service.carryOver(TARGET_DATE);

            // assert
            assertThat(carriedCount).isEqualTo(2);
            verify(repository).carryOver(SOURCE_DATE, TARGET_DATE, CARRY_OVER_RATE);
        }

        @DisplayName("서울 기준 Target 날짜가 시작됐으면 이월하지 않는다")
        @Test
        void rejectsCarryOver_whenTargetDateHasStartedInSeoul() {
            // arrange
            RankingColdStartProperties properties = new RankingColdStartProperties(CARRY_OVER_RATE);
            RankingCarryOverService service = new RankingCarryOverService(
                repository,
                properties,
                TARGET_DATE_STARTED
            );

            // act & assert
            assertThatThrownBy(() -> service.carryOver(TARGET_DATE))
                .isInstanceOf(IllegalArgumentException.class);
            verifyNoInteractions(repository);
        }
    }
}
