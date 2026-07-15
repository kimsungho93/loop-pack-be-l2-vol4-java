package com.loopers.job.ranking;

import com.loopers.batch.job.ranking.RankingRebuildJobConfig;
import com.loopers.config.redis.RedisConfig;
import com.loopers.ranking.RankingRedisKey;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = {
    "spring.batch.job.name=" + RankingRebuildJobConfig.JOB_NAME,
    "spring.batch.job.enabled=false"
})
class RankingRebuildJobE2ETest {

    private static final LocalDate PREVIOUS_DATE = LocalDate.of(2099, 7, 13);
    private static final LocalDate RANKING_DATE = LocalDate.of(2099, 7, 14);
    private static final String RANKING_DATE_PARAMETER = "20990714";

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private JobRepositoryTestUtils jobRepositoryTestUtils;

    @Autowired
    @Qualifier(RankingRebuildJobConfig.JOB_NAME)
    private Job job;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private RedisTemplate<String, String> masterRedisTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @BeforeEach
    void setUp() {
        jobLauncherTestUtils.setJob(job);
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
        jobRepositoryTestUtils.removeJobExecutions();
        jdbcTemplate.update("delete from product_metric_hourly");
        redisCleanUp.truncateAll();
    }

    @DisplayName("시간 단위 SOT로 Carry-Over를 포함한 일간 랭킹을 계산하고 기존 Redis 랭킹을 교체한다")
    @Test
    void rebuildsDailyRankingFromHourlyMetrics() throws Exception {
        // arrange
        insertMetric(PREVIOUS_DATE.atTime(10, 0), 101L, 40, 0, 0, 0);
        insertMetric(PREVIOUS_DATE.atTime(23, 0), 101L, 60, 0, 0, 0);
        insertMetric(RANKING_DATE.atTime(9, 0), 101L, 8, 0, 0, 0);
        insertMetric(RANKING_DATE.atTime(17, 0), 101L, 12, 0, 0, 0);
        insertMetric(RANKING_DATE.atTime(12, 0), 202L, 5, 1, 1, 35_000);

        String targetKey = RankingRedisKey.daily(RANKING_DATE);
        masterRedisTemplate.opsForZSet().add(targetKey, "999", 100.0);
        JobParameters jobParameters = jobParameters(1L);

        // act
        JobExecution execution = jobLauncherTestUtils.launchJob(jobParameters);

        // assert
        String tempKey = RankingRedisKey.rebuildTemp(RANKING_DATE, execution.getId());
        assertAll(
            () -> assertThat(execution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED),
            () -> assertThat(score(targetKey, "101")).isCloseTo(3.0, offset(1.0e-10)),
            () -> assertThat(score(targetKey, "202")).isCloseTo(3.15, offset(1.0e-10)),
            () -> assertThat(score(targetKey, "999")).isNull(),
            () -> assertThat(masterRedisTemplate.opsForZSet().zCard(targetKey)).isEqualTo(2),
            () -> assertThat(masterRedisTemplate.hasKey(tempKey)).isFalse()
        );
    }

    @DisplayName("같은 rankingDate라도 run.id가 다르면 SOT를 다시 읽어 새로운 랭킹으로 교체한다")
    @Test
    void rebuildsSameRankingDate_whenRunIdChanges() throws Exception {
        // arrange
        String targetKey = RankingRedisKey.daily(RANKING_DATE);
        insertMetric(RANKING_DATE.atTime(10, 0), 101L, 10, 0, 0, 0);
        JobExecution firstExecution = jobLauncherTestUtils.launchJob(jobParameters(1L));
        assertAll(
            () -> assertThat(firstExecution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED),
            () -> assertThat(score(targetKey, "101")).isCloseTo(1.0, offset(1.0e-10))
        );
        insertMetric(RANKING_DATE.atTime(11, 0), 101L, 20, 0, 0, 0);

        // act
        JobExecution secondExecution = jobLauncherTestUtils.launchJob(jobParameters(2L));

        // assert
        assertAll(
            () -> assertThat(secondExecution.getJobInstance())
                .isNotEqualTo(firstExecution.getJobInstance()),
            () -> assertThat(secondExecution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED),
            () -> assertThat(score(targetKey, "101")).isCloseTo(3.0, offset(1.0e-10))
        );
    }

    @DisplayName("완료한 rankingDate와 run.id로 다시 실행하면 중복 실행을 거부한다")
    @Test
    void rejectsRelaunch_whenJobParametersAreAlreadyComplete() throws Exception {
        // arrange
        insertMetric(RANKING_DATE.atTime(10, 0), 101L, 10, 0, 0, 0);
        JobParameters jobParameters = jobParameters(1L);
        JobExecution completedExecution = jobLauncherTestUtils.launchJob(jobParameters);
        assertThat(completedExecution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);

        // act & assert
        assertThatThrownBy(() -> jobLauncherTestUtils.launchJob(jobParameters))
            .isInstanceOf(JobInstanceAlreadyCompleteException.class);
    }

    private JobParameters jobParameters(long runId) {
        return new JobParametersBuilder()
            .addString("rankingDate", RANKING_DATE_PARAMETER)
            .addLong("run.id", runId)
            .toJobParameters();
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

    private Double score(String key, String member) {
        return masterRedisTemplate.opsForZSet().score(key, member);
    }
}
