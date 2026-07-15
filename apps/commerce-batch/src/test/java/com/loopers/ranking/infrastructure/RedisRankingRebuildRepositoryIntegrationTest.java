package com.loopers.ranking.infrastructure;

import com.loopers.config.redis.RedisConfig;
import com.loopers.ranking.RankingExpirationPolicy;
import com.loopers.ranking.RankingRedisKey;
import com.loopers.ranking.application.RankingRebuildRepository;
import com.loopers.ranking.application.RankingRebuildScore;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(properties = "spring.batch.job.enabled=false")
class RedisRankingRebuildRepositoryIntegrationTest {

    private static final LocalDate RANKING_DATE = LocalDate.of(2099, 7, 13);
    private static final long RUN_ID = 42L;

    private final RankingRebuildRepository repository;
    private final RedisTemplate<String, String> masterRedisTemplate;
    private final RedisCleanUp redisCleanUp;

    @Autowired
    RedisRankingRebuildRepositoryIntegrationTest(
        RankingRebuildRepository repository,
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

    @DisplayName("일간 Ranking을 SOT 기반 Score로 Rebuild할 때")
    @Nested
    class Replace {

        @DisplayName("임시 ZSET을 검증한 뒤 Target을 원자 교체하고 절대 만료 시각을 적용한다")
        @Test
        void replacesTargetWithRebuiltScores() {
            // arrange
            String targetKey = RankingRedisKey.daily(RANKING_DATE);
            String tempKey = RankingRedisKey.rebuildTemp(RANKING_DATE, RUN_ID);
            masterRedisTemplate.opsForZSet().add(targetKey, "303", 100.0);
            masterRedisTemplate.opsForZSet().add(tempKey, "404", 100.0);
            List<RankingRebuildScore> scores = List.of(
                new RankingRebuildScore(101L, 3.15),
                new RankingRebuildScore(202L, 0.7)
            );

            // act
            long rebuiltCount = repository.replace(RANKING_DATE, RUN_ID, scores);

            // assert
            long expectedTtl = Duration.between(
                Instant.now(),
                RankingExpirationPolicy.expiresAt(RANKING_DATE)
            ).toMillis();
            assertAll(
                () -> assertThat(rebuiltCount).isEqualTo(2),
                () -> assertThat(score(targetKey, "101")).isCloseTo(3.15, offset(1.0e-10)),
                () -> assertThat(score(targetKey, "202")).isCloseTo(0.7, offset(1.0e-10)),
                () -> assertThat(score(targetKey, "303")).isNull(),
                () -> assertThat(masterRedisTemplate.opsForZSet().zCard(targetKey)).isEqualTo(2),
                () -> assertThat(masterRedisTemplate.hasKey(tempKey)).isFalse(),
                () -> assertThat(ttl(targetKey)).isCloseTo(expectedTtl, offset(3_000L))
            );
        }

        @DisplayName("Rebuild Score가 비어 있으면 기존 Target과 임시 Key를 삭제한다")
        @Test
        void deletesTarget_whenRebuiltScoresAreEmpty() {
            // arrange
            String targetKey = RankingRedisKey.daily(RANKING_DATE);
            String tempKey = RankingRedisKey.rebuildTemp(RANKING_DATE, RUN_ID);
            masterRedisTemplate.opsForZSet().add(targetKey, "303", 100.0);
            masterRedisTemplate.opsForZSet().add(tempKey, "404", 100.0);

            // act
            long rebuiltCount = repository.replace(RANKING_DATE, RUN_ID, List.of());

            // assert
            assertAll(
                () -> assertThat(rebuiltCount).isZero(),
                () -> assertThat(masterRedisTemplate.hasKey(targetKey)).isFalse(),
                () -> assertThat(masterRedisTemplate.hasKey(tempKey)).isFalse()
            );
        }

        @DisplayName("임시 ZSET 검증에 실패하면 기존 Target을 유지하고 임시 Key를 정리한다")
        @Test
        void preservesTargetAndCleansTemp_whenValidationFails() {
            // arrange
            String targetKey = RankingRedisKey.daily(RANKING_DATE);
            String tempKey = RankingRedisKey.rebuildTemp(RANKING_DATE, RUN_ID);
            masterRedisTemplate.opsForZSet().add(targetKey, "303", 100.0);
            List<RankingRebuildScore> duplicatedScores = List.of(
                new RankingRebuildScore(101L, 1.0),
                new RankingRebuildScore(101L, 2.0)
            );

            // act & assert
            assertThatThrownBy(() -> repository.replace(RANKING_DATE, RUN_ID, duplicatedScores))
                .isInstanceOf(IllegalStateException.class);
            assertAll(
                () -> assertThat(score(targetKey, "303")).isEqualTo(100.0),
                () -> assertThat(masterRedisTemplate.hasKey(tempKey)).isFalse()
            );
        }
    }

    private Double score(String key, String member) {
        return masterRedisTemplate.opsForZSet().score(key, member);
    }

    private long ttl(String key) {
        return masterRedisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
    }
}
