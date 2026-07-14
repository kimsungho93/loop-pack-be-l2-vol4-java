package com.loopers.ranking.application;

import java.time.LocalDate;

public interface RankingCarryOverRepository {

    long carryOver(LocalDate sourceDate, LocalDate targetDate, double carryOverRate);
}
