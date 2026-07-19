package com.loopers.ranking.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

@RequiredArgsConstructor
@Service
public class RankingCarryOverService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final RankingCarryOverRepository repository;
    private final RankingColdStartProperties properties;
    private final Clock clock;

    public long carryOver(LocalDate targetDate) {
        validateBeforeTargetDate(targetDate);
        return repository.carryOver(
            targetDate.minusDays(1),
            targetDate,
            properties.carryOverRate()
        );
    }

    private void validateBeforeTargetDate(LocalDate targetDate) {
        LocalDate executionDate = LocalDate.now(clock.withZone(SEOUL));
        if (!executionDate.isBefore(targetDate)) {
            throw new IllegalArgumentException("targetDate must be after the execution date in Asia/Seoul");
        }
    }
}
