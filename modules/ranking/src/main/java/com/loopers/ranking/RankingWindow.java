package com.loopers.ranking;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public record RankingWindow(LocalDate date, int hour) {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    public static RankingWindow from(ZonedDateTime occurredAt) {
        ZonedDateTime seoulOccurredAt = occurredAt.withZoneSameInstant(SEOUL);
        return new RankingWindow(seoulOccurredAt.toLocalDate(), seoulOccurredAt.getHour());
    }
}
