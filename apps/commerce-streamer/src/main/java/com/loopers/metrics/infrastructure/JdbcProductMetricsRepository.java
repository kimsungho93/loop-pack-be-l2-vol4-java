package com.loopers.metrics.infrastructure;

import com.loopers.metrics.application.ProductMetricDelta;
import com.loopers.metrics.application.ProductMetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;

@RequiredArgsConstructor
@Component
public class JdbcProductMetricsRepository implements ProductMetricsRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void addAll(List<ProductMetricDelta> deltas, ZonedDateTime updatedAt) {
        if (deltas.isEmpty()) {
            return;
        }

        List<Object[]> batchArguments = deltas.stream()
            .map(delta -> new Object[]{
                delta.productId(),
                delta.likeCountDelta(),
                delta.viewCountDelta(),
                delta.salesCountDelta(),
                updatedAt,
                delta.likeCountDelta(),
                delta.viewCountDelta(),
                delta.salesCountDelta(),
                updatedAt
            })
            .toList();

        jdbcTemplate.batchUpdate("""
                insert into product_metrics(
                    product_id,
                    like_count,
                    view_count,
                    sales_count,
                    updated_at
                )
                values (?, ?, ?, ?, ?)
                on duplicate key update
                    like_count = like_count + ?,
                    view_count = view_count + ?,
                    sales_count = sales_count + ?,
                    updated_at = ?
                """,
            batchArguments);
    }
}
