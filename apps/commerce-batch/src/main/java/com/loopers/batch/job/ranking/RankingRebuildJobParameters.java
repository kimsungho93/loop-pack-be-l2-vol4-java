package com.loopers.batch.job.ranking;

import org.springframework.batch.core.JobParameters;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public record RankingRebuildJobParameters(LocalDate rankingDate) {

    public static final String RANKING_DATE = "rankingDate";

    public static RankingRebuildJobParameters from(JobParameters jobParameters) {
        String rankingDate = jobParameters.getString(RANKING_DATE);
        if (rankingDate == null || rankingDate.isBlank()) {
            throw new IllegalArgumentException("rankingDate is required");
        }

        try {
            return new RankingRebuildJobParameters(
                LocalDate.parse(rankingDate, DateTimeFormatter.BASIC_ISO_DATE)
            );
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("rankingDate must be a valid yyyyMMdd date", e);
        }
    }
}
