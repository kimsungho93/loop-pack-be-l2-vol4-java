package com.loopers.metrics.infrastructure;

import com.loopers.metrics.application.ProductMetricDelta;
import com.loopers.metrics.application.ProductMetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;

@RequiredArgsConstructor
@Component
public class JdbcProductMetricsRepository implements ProductMetricsRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void add(ProductMetricDelta delta, ZonedDateTime updatedAt) {
        jdbcTemplate.update("""
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
            delta.productId(),
            delta.likeCountDelta(),
            delta.viewCountDelta(),
            delta.salesCountDelta(),
            updatedAt,
            delta.likeCountDelta(),
            delta.viewCountDelta(),
            delta.salesCountDelta(),
            updatedAt);
    }
}
