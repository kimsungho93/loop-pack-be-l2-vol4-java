package com.loopers.ranking;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public final class RankingExpirationPolicy {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private RankingExpirationPolicy() {
    }

    public static Instant expiresAt(LocalDate rankingDate) {
        return rankingDate.plusDays(2).atStartOfDay(SEOUL).toInstant();
    }
}
