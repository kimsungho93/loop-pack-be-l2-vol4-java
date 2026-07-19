package com.loopers.ranking.infrastructure;

import com.loopers.ranking.application.RankingRebuildMetric;
import com.loopers.ranking.application.RankingRebuildMetricRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.batch.job.enabled=false")
class JdbcRankingRebuildMetricRepositoryIntegrationTest {

    private static final LocalDate RANKING_DATE = LocalDate.of(2026, 7, 13);

    private final RankingRebuildMetricRepository repository;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    JdbcRankingRebuildMetricRepositoryIntegrationTest(
        RankingRebuildMetricRepository repository,
        JdbcTemplate jdbcTemplate
    ) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("""
            create table if not exists product_metric_hourly (
                window_start datetime(6) not null,
                product_id bigint not null,
                view_count bigint not null,
                like_delta bigint not null,
                order_quantity bigint not null,
                order_amount bigint not null,
                updated_at datetime(6) not null,
                primary key (window_start, product_id)
            )
            """);
        jdbcTemplate.update("delete from product_metric_hourly");
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("delete from product_metric_hourly");
    }

    @DisplayName("대상 날짜까지의 시간별 Raw Metric을 날짜와 상품별 일간 Metric으로 합산한다")
    @Test
    void aggregatesHourlyMetricsByDateAndProduct_throughRankingDate() {
        // arrange
        insertMetric(RANKING_DATE.atStartOfDay(), 101L, 1, 2, 3, 10_000);
        insertMetric(RANKING_DATE.atTime(23, 0), 101L, 4, -1, 2, 25_000);
        insertMetric(RANKING_DATE.atTime(12, 0), 202L, 3, 1, 1, 15_000);
        insertMetric(RANKING_DATE.minusDays(1).atTime(23, 0), 303L, 100, 100, 100, 100_000);
        insertMetric(RANKING_DATE.plusDays(1).atStartOfDay(), 404L, 100, 100, 100, 100_000);

        // act
        List<RankingRebuildMetric> metrics = repository.findAllThrough(RANKING_DATE);

        // assert
        assertThat(metrics).containsExactlyInAnyOrder(
            new RankingRebuildMetric(RANKING_DATE.minusDays(1), 303L, 100, 100, 100_000),
            new RankingRebuildMetric(RANKING_DATE, 101L, 5, 1, 35_000),
            new RankingRebuildMetric(RANKING_DATE, 202L, 3, 1, 15_000)
        );
    }

    private void insertMetric(
        LocalDateTime windowStart,
        long productId,
        long viewCount,
        long likeDelta,
        long orderQuantity,
        long orderAmount
    ) {
        jdbcTemplate.update("""
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
                """,
            windowStart,
            productId,
            viewCount,
            likeDelta,
            orderQuantity,
            orderAmount,
            windowStart
        );
    }
}
