package com.loopers.metrics.infrastructure;

import com.loopers.metrics.application.CatalogEventEnvelope;
import com.loopers.metrics.application.CatalogEventPayload;
import com.loopers.metrics.application.CatalogEventType;
import com.loopers.metrics.application.EventHandlingMetadata;
import com.loopers.metrics.application.ProductMetricDelta;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Statement;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(properties = {
    "commerce.metrics.catalog.auto-startup=false"
})
class MetricsJdbcRepositoryIntegrationTest {

    private static final ZonedDateTime NOW = ZonedDateTime.parse("2026-07-02T10:00:00+09:00");
    private static final EventHandlingMetadata METADATA = new EventHandlingMetadata("catalog-events", 1, 20L);

    private final JdbcEventHandledRepository eventHandledRepository;
    private final JdbcProductMetricsRepository productMetricsRepository;
    private final JdbcTemplate jdbcTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    MetricsJdbcRepositoryIntegrationTest(
        JdbcEventHandledRepository eventHandledRepository,
        JdbcProductMetricsRepository productMetricsRepository,
        JdbcTemplate jdbcTemplate,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.eventHandledRepository = eventHandledRepository;
        this.productMetricsRepository = productMetricsRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("처리한 이벤트를 기록할 때")
    @Nested
    class SaveIfAbsent {

        @DisplayName("같은 eventId를 두 번 저장하면 첫 번째만 신규 처리로 판단한다.")
        @Test
        void savesOnlyOnce_whenSameEventIdIsHandledTwice() {
            // arrange
            CatalogEventEnvelope event = likedEvent("event-1");

            // act
            boolean firstSaved = eventHandledRepository.saveIfAbsent(event, METADATA, NOW);
            boolean secondSaved = eventHandledRepository.saveIfAbsent(event, METADATA, NOW);

            // assert
            assertAll(
                () -> assertThat(firstSaved).isTrue(),
                () -> assertThat(secondSaved).isFalse(),
                () -> assertThat(handledEventCount()).isEqualTo(1)
            );
        }
    }

    @DisplayName("rewriteBatchedStatements가 활성화된 Batch INSERT IGNORE 결과를 확인할 때")
    @Nested
    class BatchInsertIgnore {

        @DisplayName("반환된 Update Count로 신규 eventId를 식별할 수 없다.")
        @Test
        void doesNotIdentifyNewEventIdsFromUpdateCounts() {
            // arrange
            eventHandledRepository.saveIfAbsent(likedEvent("event-2"), METADATA, NOW);
            List<Object[]> batchArguments = List.of(
                handledEventArguments("event-1", 19L),
                handledEventArguments("event-2", 20L),
                handledEventArguments("event-3", 21L)
            );

            // act
            int[] updateCounts = jdbcTemplate.batchUpdate("""
                    insert ignore into event_handled(
                        event_id,
                        topic_name,
                        partition_no,
                        offset_no,
                        event_type,
                        aggregate_id,
                        handled_at
                    )
                    values (?, ?, ?, ?, ?, ?, ?)
                    """,
                batchArguments);

            // assert
            assertAll(
                () -> assertThat(updateCounts).containsOnly(Statement.SUCCESS_NO_INFO),
                () -> assertThat(handledEventCount()).isEqualTo(3)
            );
        }
    }

    @DisplayName("상품 지표를 반영할 때")
    @Nested
    class Add {

        @DisplayName("같은 productId의 지표를 기존 값에 누적한다.")
        @Test
        void accumulatesProductMetrics() {
            // act
            productMetricsRepository.addAll(List.of(
                new ProductMetricDelta(101L, 1, 0, 0),
                new ProductMetricDelta(101L, 1, 0, 0),
                new ProductMetricDelta(101L, 0, 1, 0),
                new ProductMetricDelta(202L, 0, 2, 0)
            ), NOW);

            // assert
            assertAll(
                () -> assertThat(likeCount(101L)).isEqualTo(2),
                () -> assertThat(viewCount(101L)).isEqualTo(1),
                () -> assertThat(salesCount(101L)).isZero(),
                () -> assertThat(viewCount(202L)).isEqualTo(2)
            );
        }

        @DisplayName("음수 delta도 클램프하지 않고 그대로 누적한다.")
        @Test
        void accumulatesNegativeDelta() {
            // act
            productMetricsRepository.addAll(
                List.of(new ProductMetricDelta(101L, -1, 0, 0)),
                NOW
            );

            // assert
            assertThat(likeCount(101L)).isEqualTo(-1);
        }
    }

    private CatalogEventEnvelope likedEvent(String eventId) {
        return new CatalogEventEnvelope(
            eventId,
            CatalogEventType.PRODUCT_LIKED,
            "PRODUCT",
            101L,
            new CatalogEventPayload(101L, 1L, null, 1),
            NOW
        );
    }

    private Object[] handledEventArguments(String eventId, long offset) {
        return new Object[]{
            eventId,
            METADATA.topicName(),
            METADATA.partitionNo(),
            offset,
            CatalogEventType.PRODUCT_LIKED.name(),
            101L,
            NOW
        };
    }

    private int handledEventCount() {
        return jdbcTemplate.queryForObject("select count(*) from event_handled", Integer.class);
    }

    private long likeCount(Long productId) {
        return jdbcTemplate.queryForObject(
            "select like_count from product_metrics where product_id = ?",
            Long.class,
            productId
        );
    }

    private long viewCount(Long productId) {
        return jdbcTemplate.queryForObject(
            "select view_count from product_metrics where product_id = ?",
            Long.class,
            productId
        );
    }

    private long salesCount(Long productId) {
        return jdbcTemplate.queryForObject(
            "select sales_count from product_metrics where product_id = ?",
            Long.class,
            productId
        );
    }
}
