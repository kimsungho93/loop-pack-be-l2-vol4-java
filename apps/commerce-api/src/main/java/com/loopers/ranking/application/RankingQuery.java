package com.loopers.ranking.application;

import java.time.LocalDate;
import java.util.Optional;

public interface RankingQuery {

    RankingEntries findDaily(LocalDate date, long start, long end);

    Optional<Long> findDailyRank(LocalDate date, Long productId);
}
