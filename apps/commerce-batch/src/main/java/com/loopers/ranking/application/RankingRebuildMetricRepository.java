package com.loopers.ranking.application;

import java.time.LocalDate;
import java.util.List;

public interface RankingRebuildMetricRepository {

    List<RankingRebuildMetric> findAllThrough(LocalDate rankingDate);
}
