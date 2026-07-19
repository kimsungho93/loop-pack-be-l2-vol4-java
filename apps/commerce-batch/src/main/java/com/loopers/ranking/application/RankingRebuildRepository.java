package com.loopers.ranking.application;

import java.time.LocalDate;
import java.util.List;

public interface RankingRebuildRepository {

    long replace(LocalDate rankingDate, long runId, List<RankingRebuildScore> scores);
}
