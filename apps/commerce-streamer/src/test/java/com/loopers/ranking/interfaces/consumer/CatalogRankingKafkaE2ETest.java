package com.loopers.ranking.interfaces.consumer;

import com.loopers.config.redis.RedisConfig;
import com.loopers.metrics.application.CatalogEventEnvelope;
import com.loopers.metrics.application.CatalogEventPayload;
import com.loopers.metrics.application.CatalogEventType;
import com.loopers.ranking.RankingRedisKey;
import com.loopers.ranking.RankingWindow;
import com.loopers.utils.RedisCleanUp;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

@Testcontainers
@SpringBootTest(properties = {
    "commerce.metrics.catalog.auto-startup=false",
    "commerce.ranking.catalog.auto-startup=true",
    "commerce.ranking.catalog.group-id=catalog-ranking-e2e",
    "spring.kafka.consumer.auto-offset-reset=earliest",
    "spring.kafka.properties.auto.offset.reset=earliest"
})
class CatalogRankingKafkaE2ETest {

    private static final String TOPIC = "catalog-events";
    private static final String DLT_TOPIC = TOPIC + ".DLT";
    private static final String GROUP_ID = "catalog-ranking-e2e";
    private static final long PRODUCT_ID = 101L;
    private static final LocalDate RANKING_DATE = LocalDate.of(2099, 7, 13);
    private static final RankingWindow WINDOW = new RankingWindow(RANKING_DATE, 10);
    private static final ZonedDateTime OCCURRED_AT = ZonedDateTime.parse("2099-07-13T10:30:00+09:00");
    private static final long AWAIT_SECONDS = 20;

    @Container
    private static final KafkaContainer KAFKA = new KafkaContainer(
        DockerImageName.parse("apache/kafka-native:3.8.0")
    );

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final RedisTemplate<String, String> masterRedisTemplate;
    private final RedisCleanUp redisCleanUp;

    @Autowired
    CatalogRankingKafkaE2ETest(
        KafkaTemplate<Object, Object> kafkaTemplate,
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> masterRedisTemplate,
        RedisCleanUp redisCleanUp
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.masterRedisTemplate = masterRedisTemplate;
        this.redisCleanUp = redisCleanUp;
    }

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.kafka.admin.properties.bootstrap.servers", KAFKA::getBootstrapServers);
    }

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("catalog-events의 조회와 좋아요 이벤트를 일간·시간 Ranking에 반영한다.")
    @Test
    void appliesCatalogEventsToDailyAndHourlyRanking() throws Exception {
        // arrange
        CatalogEventEnvelope viewed = event("event-ranking-1", CatalogEventType.PRODUCT_VIEWED, null);
        CatalogEventEnvelope liked = event("event-ranking-2", CatalogEventType.PRODUCT_LIKED, 1);

        // act
        send(viewed);
        send(liked);

        // assert
        awaitRanking(0.3, 2);
        assertThat(score(RankingRedisKey.daily(RANKING_DATE))).isCloseTo(0.3, offset(1.0e-10));
        assertThat(score(RankingRedisKey.hourly(WINDOW))).isCloseTo(0.3, offset(1.0e-10));
        assertThat(handledCount()).isEqualTo(2);
    }

    @DisplayName("같은 eventId를 Kafka로 다시 전달해도 Ranking 점수를 중복 반영하지 않는다.")
    @Test
    void doesNotApplyDuplicateEventScore() throws Exception {
        // arrange
        CatalogEventEnvelope event = event("event-ranking-duplicate", CatalogEventType.PRODUCT_VIEWED, null);
        send(event);
        awaitRanking(0.1, 1);

        // act
        SendResult<Object, Object> duplicate = send(event);
        awaitCommittedOffset(
            duplicate.getRecordMetadata().partition(),
            duplicate.getRecordMetadata().offset() + 1
        );

        // assert
        assertThat(score(RankingRedisKey.daily(RANKING_DATE))).isCloseTo(0.1, offset(1.0e-10));
        assertThat(score(RankingRedisKey.hourly(WINDOW))).isCloseTo(0.1, offset(1.0e-10));
        assertThat(handledCount()).isEqualTo(1);
    }

    private SendResult<Object, Object> send(CatalogEventEnvelope event) throws Exception {
        SendResult<Object, Object> result = kafkaTemplate.send(TOPIC, String.valueOf(PRODUCT_ID), event)
            .get(10, TimeUnit.SECONDS);
        kafkaTemplate.flush();
        return result;
    }

    private void awaitRanking(double expectedScore, long expectedHandledCount) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(AWAIT_SECONDS);
        while (!matches(expectedScore, expectedHandledCount) && System.nanoTime() < deadline) {
            Thread.sleep(200);
        }
        assertThat(matches(expectedScore, expectedHandledCount)).isTrue();
    }

    private boolean matches(double expectedScore, long expectedHandledCount) {
        Double dailyScore = score(RankingRedisKey.daily(RANKING_DATE));
        return dailyScore != null
            && Math.abs(dailyScore - expectedScore) <= 1.0e-10
            && handledCount() == expectedHandledCount;
    }

    private void awaitCommittedOffset(int partition, long expectedOffset) throws Exception {
        TopicPartition topicPartition = new TopicPartition(TOPIC, partition);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(AWAIT_SECONDS);
        try (AdminClient adminClient = AdminClient.create(Map.of(
            AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
            KAFKA.getBootstrapServers()
        ))) {
            while (System.nanoTime() < deadline) {
                Map<TopicPartition, OffsetAndMetadata> offsets = adminClient
                    .listConsumerGroupOffsets(GROUP_ID)
                    .partitionsToOffsetAndMetadata()
                    .get(5, TimeUnit.SECONDS);
                OffsetAndMetadata committed = offsets.get(topicPartition);
                if (committed != null && committed.offset() >= expectedOffset) {
                    return;
                }
                Thread.sleep(200);
            }
        }
        throw new AssertionError("Ranking consumer offset was not committed.");
    }

    private Double score(String key) {
        return masterRedisTemplate.opsForZSet().score(key, String.valueOf(PRODUCT_ID));
    }

    private long handledCount() {
        Long count = masterRedisTemplate.opsForSet().size(RankingRedisKey.handled(RANKING_DATE));
        return count == null ? 0L : count;
    }

    private CatalogEventEnvelope event(String eventId, CatalogEventType eventType, Integer delta) {
        return new CatalogEventEnvelope(
            eventId,
            eventType,
            "PRODUCT",
            PRODUCT_ID,
            new CatalogEventPayload(PRODUCT_ID, 1L, null, delta),
            OCCURRED_AT
        );
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

        @Bean
        NewTopic catalogEventsDltTopic() {
            return TopicBuilder.name(DLT_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
        }
    }
}
