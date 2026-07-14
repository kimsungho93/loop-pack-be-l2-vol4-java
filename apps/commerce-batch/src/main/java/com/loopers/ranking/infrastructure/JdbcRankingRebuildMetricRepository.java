package com.loopers.ranking.infrastructure;

import com.loopers.ranking.application.RankingRebuildMetric;
import com.loopers.ranking.application.RankingRebuildMetricRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RequiredArgsConstructor
@Component
public class JdbcRankingRebuildMetricRepository implements RankingRebuildMetricRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<RankingRebuildMetric> findAllByDate(LocalDate rankingDate) {
        LocalDateTime windowStart = rankingDate.atStartOfDay();
        LocalDateTime windowEnd = rankingDate.plusDays(1).atStartOfDay();

        return jdbcTemplate.query(
            """
                select
                    product_id,
                    sum(view_count) as view_count,
                    sum(like_delta) as like_delta,
                    sum(order_amount) as order_amount
                from product_metric_hourly
                where window_start >= ? and window_start < ?
                group by product_id
                """,
            (resultSet, rowNumber) -> new RankingRebuildMetric(
                resultSet.getLong("product_id"),
                resultSet.getLong("view_count"),
                resultSet.getLong("like_delta"),
                resultSet.getLong("order_amount")
            ),
            windowStart,
            windowEnd
        );
    }
}
