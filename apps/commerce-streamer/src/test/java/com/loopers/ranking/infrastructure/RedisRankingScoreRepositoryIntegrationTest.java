package com.loopers.ranking.infrastructure;

import com.loopers.config.redis.RedisConfig;
import com.loopers.ranking.RankingExpirationPolicy;
import com.loopers.ranking.RankingRedisKey;
import com.loopers.ranking.RankingWindow;
import com.loopers.ranking.application.RankingScoreBatch;
import com.loopers.ranking.application.RankingScoreDelta;
import com.loopers.ranking.application.RankingScoreRepository;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

@SpringBootTest(properties = {
    "commerce.metrics.catalog.auto-startup=false"
})
class RedisRankingScoreRepositoryIntegrationTest {

    private static final LocalDate RANKING_DATE = LocalDate.of(2099, 7, 13);
    private static final RankingWindow WINDOW = new RankingWindow(RANKING_DATE, 10);
    private static final long PRODUCT_ID = 101L;

    private final RankingScoreRepository repository;
    private final RedisTemplate<String, String> masterRedisTemplate;
    private final RedisCleanUp redisCleanUp;

    @Autowired
    RedisRankingScoreRepositoryIntegrationTest(
        RankingScoreRepository repository,
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> masterRedisTemplate,
        RedisCleanUp redisCleanUp
    ) {
        this.repository = repository;
        this.masterRedisTemplate = masterRedisTemplate;
        this.redisCleanUp = redisCleanUp;
    }

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("상품 Ranking 점수를 반영할 때")
    @Nested
    class Apply {

        @DisplayName("처음 처리하는 이벤트 점수 합계를 일간과 시간 Ranking에 반영한다.")
        @Test
        void appliesUnseenEventScores() {
            // arrange
            RankingScoreBatch batch = batch(
                new RankingScoreDelta("event-1", 0.1),
                new RankingScoreDelta("event-2", 0.2)
            );

            // act
            long appliedCount = repository.apply(batch);

            // assert
            assertThat(appliedCount).isEqualTo(2);
            assertThat(score(RankingRedisKey.daily(RANKING_DATE))).isCloseTo(0.3, offset(1.0e-10));
            assertThat(score(RankingRedisKey.hourly(WINDOW))).isCloseTo(0.3, offset(1.0e-10));
            assertThat(handledCount()).isEqualTo(2);
        }

        @DisplayName("같은 eventId를 다시 처리하면 Ranking 점수를 중복 반영하지 않는다.")
        @Test
        void skipsAlreadyHandledEvent() {
            // arrange
            repository.apply(batch(new RankingScoreDelta("event-1", 0.1)));

            // act
            long appliedCount = repository.apply(batch(new RankingScoreDelta("event-1", 10.0)));

            // assert
            assertThat(appliedCount).isZero();
            assertThat(score(RankingRedisKey.daily(RANKING_DATE))).isCloseTo(0.1, offset(1.0e-10));
            assertThat(handledCount()).isEqualTo(1);
        }

        @DisplayName("같은 Batch 안의 중복 eventId도 Ranking 점수에 한 번만 반영한다.")
        @Test
        void appliesDuplicateEventOnceInSameBatch() {
            // arrange
            RankingScoreBatch batch = batch(
                new RankingScoreDelta("event-1", 0.1),
                new RankingScoreDelta("event-1", 0.1)
            );

            // act
            long appliedCount = repository.apply(batch);

            // assert
            assertThat(appliedCount).isEqualTo(1);
            assertThat(score(RankingRedisKey.daily(RANKING_DATE))).isCloseTo(0.1, offset(1.0e-10));
            assertThat(handledCount()).isEqualTo(1);
        }

        @DisplayName("일부 성공한 Batch를 재처리하면 아직 처리하지 않은 이벤트만 추가한다.")
        @Test
        void appliesOnlyUnseenEvents_whenBatchIsRetried() {
            // arrange
            repository.apply(batch(new RankingScoreDelta("event-1", 0.1)));
            RankingScoreBatch retriedBatch = batch(
                new RankingScoreDelta("event-1", 0.1),
                new RankingScoreDelta("event-2", 0.2)
            );

            // act
            long appliedCount = repository.apply(retriedBatch);

            // assert
            assertThat(appliedCount).isEqualTo(1);
            assertThat(score(RankingRedisKey.daily(RANKING_DATE))).isCloseTo(0.3, offset(1.0e-10));
            assertThat(handledCount()).isEqualTo(2);
        }

        @DisplayName("일간·시간·중복 처리 Key에 같은 절대 만료 시각을 적용한다.")
        @Test
        void appliesSameAbsoluteExpiration() {
            // act
            repository.apply(batch(new RankingScoreDelta("event-1", 0.1)));

            // assert
            long expectedTtl = Duration.between(
                Instant.now(),
                RankingExpirationPolicy.expiresAt(RANKING_DATE)
            ).toMillis();
            assertThat(ttl(RankingRedisKey.daily(RANKING_DATE))).isCloseTo(expectedTtl, offset(3_000L));
            assertThat(ttl(RankingRedisKey.hourly(WINDOW))).isCloseTo(expectedTtl, offset(3_000L));
            assertThat(ttl(RankingRedisKey.handled(RANKING_DATE))).isCloseTo(expectedTtl, offset(3_000L));
        }

        @DisplayName("Ranking Key 타입이 잘못되면 어떤 Key도 변경하지 않고 실패한다.")
        @Test
        void failsWithoutPartialUpdate_whenKeyTypeIsInvalid() {
            // arrange
            masterRedisTemplate.opsForValue().set(RankingRedisKey.hourly(WINDOW), "invalid-type");

            // act & assert
            assertThatThrownBy(() -> repository.apply(batch(new RankingScoreDelta("event-1", 0.1))))
                .isInstanceOf(DataAccessException.class);
            assertThat(masterRedisTemplate.opsForZSet().score(
                RankingRedisKey.daily(RANKING_DATE),
                String.valueOf(PRODUCT_ID)
            )).isNull();
            assertThat(handledCount()).isZero();
        }
    }

    private RankingScoreBatch batch(RankingScoreDelta... deltas) {
        return new RankingScoreBatch(WINDOW, PRODUCT_ID, List.of(deltas));
    }

    private double score(String key) {
        return masterRedisTemplate.opsForZSet().score(key, String.valueOf(PRODUCT_ID));
    }

    private long handledCount() {
        Long count = masterRedisTemplate.opsForSet().size(RankingRedisKey.handled(RANKING_DATE));
        return count == null ? 0L : count;
    }

    private long ttl(String key) {
        return masterRedisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
    }
}
