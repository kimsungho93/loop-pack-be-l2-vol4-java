package com.loopers.ranking.application;

import java.time.LocalDate;

public interface RankingQuery {

    RankingEntries findDaily(LocalDate date, long start, long end);
}
