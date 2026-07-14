package com.loopers.ranking.infrastructure;

import com.loopers.config.redis.RedisConfig;
import com.loopers.ranking.RankingExpirationPolicy;
import com.loopers.ranking.RankingRedisKey;
import com.loopers.ranking.application.RankingCarryOverRepository;
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
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(properties = "spring.batch.job.enabled=false")
class RedisRankingCarryOverRepositoryIntegrationTest {

    private static final LocalDate SOURCE_DATE = LocalDate.of(2099, 7, 13);
    private static final LocalDate TARGET_DATE = LocalDate.of(2099, 7, 14);
    private static final double CARRY_OVER_RATE = 0.1;

    private final RankingCarryOverRepository repository;
    private final RedisTemplate<String, String> masterRedisTemplate;
    private final RedisCleanUp redisCleanUp;

    @Autowired
    RedisRankingCarryOverRepositoryIntegrationTest(
        RankingCarryOverRepository repository,
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

    @DisplayName("일간 Ranking을 다음 날짜로 이월할 때")
    @Nested
    class CarryOver {

        @DisplayName("Source Score의 10%로 Target을 원자 교체하고 Target 만료 시각을 적용한다")
        @Test
        void replacesTargetWithWeightedSourceScores() {
            // arrange
            String sourceKey = RankingRedisKey.daily(SOURCE_DATE);
            String targetKey = RankingRedisKey.daily(TARGET_DATE);
            String tempKey = RankingRedisKey.carryOverTemp(TARGET_DATE);
            masterRedisTemplate.opsForZSet().add(sourceKey, "101", 10.0);
            masterRedisTemplate.opsForZSet().add(sourceKey, "202", 25.0);
            masterRedisTemplate.opsForZSet().add(targetKey, "303", 100.0);
            masterRedisTemplate.opsForZSet().add(tempKey, "404", 100.0);

            // act
            long carriedCount = repository.carryOver(SOURCE_DATE, TARGET_DATE, CARRY_OVER_RATE);

            // assert
            long expectedTtl = Duration.between(
                Instant.now(),
                RankingExpirationPolicy.expiresAt(TARGET_DATE)
            ).toMillis();
            assertAll(
                () -> assertThat(carriedCount).isEqualTo(2),
                () -> assertThat(score(targetKey, "101")).isCloseTo(1.0, offset(1.0e-10)),
                () -> assertThat(score(targetKey, "202")).isCloseTo(2.5, offset(1.0e-10)),
                () -> assertThat(score(targetKey, "303")).isNull(),
                () -> assertThat(masterRedisTemplate.opsForZSet().zCard(targetKey)).isEqualTo(2),
                () -> assertThat(masterRedisTemplate.hasKey(tempKey)).isFalse(),
                () -> assertThat(ttl(targetKey)).isCloseTo(expectedTtl, offset(3_000L))
            );
        }

        @DisplayName("Source가 비어 있으면 Target을 만들지 않고 남은 임시 Key를 정리한다")
        @Test
        void skipsTargetCreation_whenSourceIsEmpty() {
            // arrange
            String targetKey = RankingRedisKey.daily(TARGET_DATE);
            String tempKey = RankingRedisKey.carryOverTemp(TARGET_DATE);
            masterRedisTemplate.opsForZSet().add(tempKey, "404", 100.0);

            // act
            long carriedCount = repository.carryOver(SOURCE_DATE, TARGET_DATE, CARRY_OVER_RATE);

            // assert
            assertAll(
                () -> assertThat(carriedCount).isZero(),
                () -> assertThat(masterRedisTemplate.hasKey(targetKey)).isFalse(),
                () -> assertThat(masterRedisTemplate.hasKey(tempKey)).isFalse()
            );
        }

        @DisplayName("Source 조회에 실패하면 기존 Target을 변경하지 않는다")
        @Test
        void preservesTarget_whenSourceLookupFails() {
            // arrange
            String sourceKey = RankingRedisKey.daily(SOURCE_DATE);
            String targetKey = RankingRedisKey.daily(TARGET_DATE);
            masterRedisTemplate.opsForValue().set(sourceKey, "invalid-type");
            masterRedisTemplate.opsForZSet().add(targetKey, "303", 100.0);

            // act & assert
            assertThatThrownBy(() -> repository.carryOver(SOURCE_DATE, TARGET_DATE, CARRY_OVER_RATE))
                .isInstanceOf(DataAccessException.class);
            assertThat(score(targetKey, "303")).isEqualTo(100.0);
        }
    }

    private Double score(String key, String member) {
        return masterRedisTemplate.opsForZSet().score(key, member);
    }

    private long ttl(String key) {
        return masterRedisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
    }
}
