package com.loopers.metrics.infrastructure;

import com.loopers.metrics.application.ProductMetricHourlyDelta;
import com.loopers.metrics.application.ProductMetricHourlyRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "commerce.metrics.catalog.auto-startup=false"
})
class ProductMetricHourlyJdbcRepositoryIntegrationTest {

    private static final LocalDateTime WINDOW_START = LocalDateTime.of(2026, 7, 13, 10, 0);
    private static final ZonedDateTime UPDATED_AT = ZonedDateTime.parse("2026-07-13T10:30:00+09:00");

    private final ProductMetricHourlyRepository repository;
    private final JdbcTemplate jdbcTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    ProductMetricHourlyJdbcRepositoryIntegrationTest(
        ProductMetricHourlyRepository repository,
        JdbcTemplate jdbcTemplate,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("같은 시간 Window와 상품의 Raw Metric을 기존 값에 누적한다.")
    @Test
    void accumulatesRawMetricsInSameWindow() {
        // arrange
        ProductMetricHourlyDelta first = new ProductMetricHourlyDelta(WINDOW_START, 101L, 1, 2, 3, 25_000);
        ProductMetricHourlyDelta second = new ProductMetricHourlyDelta(WINDOW_START, 101L, 4, -1, 2, 10_000);

        // act
        repository.add(first, UPDATED_AT);
        repository.add(second, UPDATED_AT.plusMinutes(1));

        // assert
        assertThat(findMetric(WINDOW_START, 101L)).isEqualTo(new HourlyMetric(5, 1, 5, 35_000));
    }

    @DisplayName("상품이 같아도 시간 Window가 다르면 별도 Raw Metric으로 저장한다.")
    @Test
    void storesMetricsInDifferentWindows() {
        // arrange
        LocalDateTime nextWindowStart = WINDOW_START.plusHours(1);

        // act
        repository.add(new ProductMetricHourlyDelta(WINDOW_START, 101L, 1, 0, 0, 0), UPDATED_AT);
        repository.add(new ProductMetricHourlyDelta(nextWindowStart, 101L, 1, 0, 0, 0), UPDATED_AT);

        // assert
        assertThat(metricCount()).isEqualTo(2);
    }

    private HourlyMetric findMetric(LocalDateTime windowStart, Long productId) {
        return jdbcTemplate.queryForObject(
            """
                select view_count, like_delta, order_quantity, order_amount
                from product_metric_hourly
                where window_start = ? and product_id = ?
                """,
            (resultSet, rowNumber) -> new HourlyMetric(
                resultSet.getLong("view_count"),
                resultSet.getLong("like_delta"),
                resultSet.getLong("order_quantity"),
                resultSet.getLong("order_amount")
            ),
            windowStart,
            productId
        );
    }

    private int metricCount() {
        return jdbcTemplate.queryForObject("select count(*) from product_metric_hourly", Integer.class);
    }

    private record HourlyMetric(
        long viewCount,
        long likeDelta,
        long orderQuantity,
        long orderAmount
    ) {
    }
}
