package com.loopers.metrics.interfaces.consumer;

import com.loopers.metrics.application.CatalogEventEnvelope;
import com.loopers.metrics.application.CatalogEventPayload;
import com.loopers.metrics.application.CatalogEventType;
import com.loopers.utils.DatabaseCleanUp;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.ZonedDateTime;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@Testcontainers
@SpringBootTest(properties = {
    "commerce.metrics.catalog.topic-name=catalog-events",
    "commerce.metrics.catalog.group-id=catalog-metrics-e2e",
    "spring.kafka.consumer.auto-offset-reset=earliest",
    "spring.kafka.properties.auto.offset.reset=earliest"
})
class CatalogMetricsKafkaE2ETest {

    private static final String TOPIC = "catalog-events";
    private static final Long PRODUCT_ID = 101L;
    private static final Long USER_ID = 1L;
    private static final ZonedDateTime OCCURRED_AT = ZonedDateTime.parse("2026-07-02T10:00:00+09:00");

    @Container
    private static final KafkaContainer KAFKA = new KafkaContainer(
        DockerImageName.parse("apache/kafka-native:3.8.0")
    );

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    CatalogMetricsKafkaE2ETest(
        KafkaTemplate<Object, Object> kafkaTemplate,
        JdbcTemplate jdbcTemplate,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.jdbcTemplate = jdbcTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.kafka.admin.properties.bootstrap.servers", KAFKA::getBootstrapServers);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("catalog-events를 실제 Kafka로 소비하면, 상품 지표와 처리 이력이 저장된다.")
    @Test
    void consumesCatalogEventsFromKafka() throws Exception {
        // arrange
        CatalogEventEnvelope liked = event("event-kafka-1", CatalogEventType.PRODUCT_LIKED, 1);
        CatalogEventEnvelope viewed = event("event-kafka-2", CatalogEventType.PRODUCT_VIEWED, 1);

        // act
        SendResult<Object, Object> first = send(liked);
        SendResult<Object, Object> second = send(viewed);

        // assert
        ProductMetricRow metric = awaitMetric(PRODUCT_ID, 1, 1);
        assertAll(
            () -> assertThat(first.getRecordMetadata().partition())
                .isEqualTo(second.getRecordMetadata().partition()),
            () -> assertThat(metric.likeCount()).isEqualTo(1),
            () -> assertThat(metric.viewCount()).isEqualTo(1),
            () -> assertThat(handledEventCount()).isEqualTo(2)
        );
    }

    private SendResult<Object, Object> send(CatalogEventEnvelope event) throws Exception {
        SendResult<Object, Object> result = kafkaTemplate.send(TOPIC, PRODUCT_ID.toString(), event)
            .get(10, TimeUnit.SECONDS);
        kafkaTemplate.flush();
        return result;
    }

    private ProductMetricRow awaitMetric(Long productId, long likeCount, long viewCount) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        ProductMetricRow metric = findMetric(productId);
        while (!matches(metric, likeCount, viewCount) && System.nanoTime() < deadline) {
            Thread.sleep(200);
            metric = findMetric(productId);
        }
        assertThat(metric).isNotNull();
        return metric;
    }

    private boolean matches(ProductMetricRow metric, long likeCount, long viewCount) {
        return metric != null
            && metric.likeCount() == likeCount
            && metric.viewCount() == viewCount;
    }

    private ProductMetricRow findMetric(Long productId) {
        try {
            return jdbcTemplate.queryForObject(
                """
                    select like_count, view_count
                    from product_metrics
                    where product_id = ?
                    """,
                (resultSet, rowNum) -> new ProductMetricRow(
                    resultSet.getLong("like_count"),
                    resultSet.getLong("view_count")
                ),
                productId
            );
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private int handledEventCount() {
        return jdbcTemplate.queryForObject("select count(*) from event_handled", Integer.class);
    }

    private CatalogEventEnvelope event(String eventId, CatalogEventType eventType, int delta) {
        return new CatalogEventEnvelope(
            eventId,
            eventType,
            "PRODUCT",
            PRODUCT_ID,
            new CatalogEventPayload(PRODUCT_ID, USER_ID, null, delta),
            OCCURRED_AT
        );
    }

    private record ProductMetricRow(long likeCount, long viewCount) {
    }

    @TestConfiguration
    static class KafkaTopicTestConfig {

        @Bean
        NewTopic catalogEventsTopic() {
            return TopicBuilder.name(TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
        }
    }
}
