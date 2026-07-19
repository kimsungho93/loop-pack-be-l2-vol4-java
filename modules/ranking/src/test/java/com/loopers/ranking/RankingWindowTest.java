package com.loopers.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class RankingWindowTest {

    @DisplayName("이벤트 발생 시각을 서울 시간의 날짜와 시간 Window로 변환한다.")
    @Test
    void convertsOccurredAtToSeoulWindow() {
        // arrange
        ZonedDateTime occurredAt = ZonedDateTime.parse("2026-07-12T15:30:00Z");

        // act
        RankingWindow window = RankingWindow.from(occurredAt);

        // assert
        assertThat(window).isEqualTo(new RankingWindow(LocalDate.of(2026, 7, 13), 0));
    }
}
