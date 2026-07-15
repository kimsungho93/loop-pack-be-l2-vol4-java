package com.loopers.ranking;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public final class RankingRedisKey {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private RankingRedisKey() {
    }

    public static String daily(LocalDate date) {
        return "ranking:all:{%s}".formatted(format(date));
    }

    public static String hourly(RankingWindow window) {
        return "ranking:hourly:{%s}:%02d".formatted(format(window.date()), window.hour());
    }

    public static String handled(LocalDate date) {
        return "ranking:handled:{%s}".formatted(format(date));
    }

    public static String carryOverTemp(LocalDate date) {
        return "ranking:all:{%s}:carry-over:temp".formatted(format(date));
    }

    public static String rebuildTemp(LocalDate date, long runId) {
        return "ranking:rebuild:{%s}:%d".formatted(format(date), runId);
    }

    private static String format(LocalDate date) {
        return date.format(DATE_FORMATTER);
    }
}
