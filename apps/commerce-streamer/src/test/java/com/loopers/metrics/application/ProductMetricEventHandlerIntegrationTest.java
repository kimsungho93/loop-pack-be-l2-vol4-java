package com.loopers.metrics.application;

import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "commerce.metrics.catalog.auto-startup=false"
})
class ProductMetricEventHandlerIntegrationTest {

    private static final ZonedDateTime OCCURRED_AT = ZonedDateTime.parse("2026-07-13T10:30:00+09:00");
    private static final EventHandlingMetadata METADATA = new EventHandlingMetadata("catalog-events", 0, 10L);

    private final ProductMetricEventHandler handler;
    private final JdbcTemplate jdbcTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    ProductMetricEventHandlerIntegrationTest(
        ProductMetricEventHandler handler,
        JdbcTemplate jdbcTemplate,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.handler = handler;
        this.jdbcTemplate = jdbcTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("같은 eventId를 다시 처리하면 시간 단위 Raw Metric을 중복 반영하지 않는다.")
    @Test
    void skipsHourlyMetric_whenEventIsHandledTwice() {
        // arrange
        CatalogEventEnvelope event = viewedEvent("event-1");

        // act
        handler.handle(event, METADATA);
        handler.handle(event, METADATA);

        // assert
        assertThat(handledEventCount()).isEqualTo(1);
        assertThat(hourlyViewCount()).isEqualTo(1);
    }

    private CatalogEventEnvelope viewedEvent(String eventId) {
        return new CatalogEventEnvelope(
            eventId,
            CatalogEventType.PRODUCT_VIEWED,
            "PRODUCT",
            101L,
            new CatalogEventPayload(101L, 1L, 1L, null),
            OCCURRED_AT
        );
    }

    private int handledEventCount() {
        return jdbcTemplate.queryForObject("select count(*) from event_handled", Integer.class);
    }

    private long hourlyViewCount() {
        return jdbcTemplate.queryForObject(
            "select view_count from product_metric_hourly where product_id = ?",
            Long.class,
            101L
        );
    }
}
