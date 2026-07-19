package com.loopers.batch.job.ranking;

import org.springframework.batch.core.JobParameters;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public record RankingCarryOverJobParameters(LocalDate targetDate) {

    public static final String TARGET_DATE = "targetDate";

    public static RankingCarryOverJobParameters from(JobParameters jobParameters) {
        String targetDate = jobParameters.getString(TARGET_DATE);
        if (targetDate == null || targetDate.isBlank()) {
            throw new IllegalArgumentException("targetDate is required");
        }

        try {
            return new RankingCarryOverJobParameters(
                LocalDate.parse(targetDate, DateTimeFormatter.BASIC_ISO_DATE)
            );
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("targetDate must be a valid yyyyMMdd date", e);
        }
    }
}
