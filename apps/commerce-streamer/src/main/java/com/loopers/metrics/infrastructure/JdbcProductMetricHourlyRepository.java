package com.loopers.metrics.infrastructure;

import com.loopers.metrics.application.ProductMetricHourlyDelta;
import com.loopers.metrics.application.ProductMetricHourlyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;

@RequiredArgsConstructor
@Component
public class JdbcProductMetricHourlyRepository implements ProductMetricHourlyRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void addAll(List<ProductMetricHourlyDelta> deltas, ZonedDateTime updatedAt) {
        if (deltas.isEmpty()) {
            return;
        }

        List<Object[]> batchArguments = deltas.stream()
            .map(delta -> new Object[]{
                delta.windowStart(),
                delta.productId(),
                delta.viewCountDelta(),
                delta.likeDelta(),
                delta.orderQuantityDelta(),
                delta.orderAmountDelta(),
                updatedAt,
                delta.viewCountDelta(),
                delta.likeDelta(),
                delta.orderQuantityDelta(),
                delta.orderAmountDelta(),
                updatedAt
            })
            .toList();

        jdbcTemplate.batchUpdate("""
                insert into product_metric_hourly(
                    window_start,
                    product_id,
                    view_count,
                    like_delta,
                    order_quantity,
                    order_amount,
                    updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?)
                on duplicate key update
                    view_count = view_count + ?,
                    like_delta = like_delta + ?,
                    order_quantity = order_quantity + ?,
                    order_amount = order_amount + ?,
                    updated_at = ?
                """,
            batchArguments);
    }
}
