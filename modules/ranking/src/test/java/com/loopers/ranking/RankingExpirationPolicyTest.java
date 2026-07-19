package com.loopers.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class RankingExpirationPolicyTest {

    @DisplayName("Ranking Key는 이벤트 날짜 시작점부터 2일 뒤 서울 자정에 만료한다.")
    @Test
    void calculatesAbsoluteExpiration() {
        // arrange
        LocalDate rankingDate = LocalDate.of(2026, 7, 12);

        // act
        Instant expiresAt = RankingExpirationPolicy.expiresAt(rankingDate);

        // assert
        assertThat(expiresAt).isEqualTo(Instant.parse("2026-07-13T15:00:00Z"));
    }
}
