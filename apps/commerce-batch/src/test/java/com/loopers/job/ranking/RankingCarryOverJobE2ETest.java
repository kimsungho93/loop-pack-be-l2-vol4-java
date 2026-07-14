package com.loopers.job.ranking;

import com.loopers.batch.job.ranking.RankingCarryOverJobConfig;
import com.loopers.config.redis.RedisConfig;
import com.loopers.ranking.RankingRedisKey;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = {
    "spring.batch.job.name=" + RankingCarryOverJobConfig.JOB_NAME,
    "spring.batch.job.enabled=false"
})
class RankingCarryOverJobE2ETest {

    private static final LocalDate SOURCE_DATE = LocalDate.of(2099, 7, 13);
    private static final LocalDate TARGET_DATE = LocalDate.of(2099, 7, 14);
    private static final String TARGET_DATE_PARAMETER = "20990714";

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private JobRepositoryTestUtils jobRepositoryTestUtils;

    @Autowired
    @Qualifier(RankingCarryOverJobConfig.JOB_NAME)
    private Job job;

    @Autowired
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private RedisTemplate<String, String> masterRedisTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @BeforeEach
    void setUp() {
        jobLauncherTestUtils.setJob(job);
    }

    @AfterEach
    void tearDown() {
        jobRepositoryTestUtils.removeJobExecutions();
        redisCleanUp.truncateAll();
    }

    @DisplayName("rankingCarryOverJob을 실행할 때")
    @Nested
    class Launch {

        @DisplayName("유효한 targetDate가 주어지면 전날 점수의 10%로 Target Ranking을 생성한다")
        @Test
        void completesCarryOver_withValidTargetDate() throws Exception {
            // arrange
            String sourceKey = RankingRedisKey.daily(SOURCE_DATE);
            String targetKey = RankingRedisKey.daily(TARGET_DATE);
            masterRedisTemplate.opsForZSet().add(sourceKey, "101", 10.0);
            masterRedisTemplate.opsForZSet().add(sourceKey, "202", 25.0);
            JobParameters jobParameters = jobParameters(TARGET_DATE_PARAMETER);

            // act
            JobExecution execution = jobLauncherTestUtils.launchJob(jobParameters);

            // assert
            assertAll(
                () -> assertThat(execution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED),
                () -> assertThat(score(targetKey, "101")).isCloseTo(1.0, offset(1.0e-10)),
                () -> assertThat(score(targetKey, "202")).isCloseTo(2.5, offset(1.0e-10))
            );
        }

        @DisplayName("targetDate가 누락되면 Job을 시작하지 않는다")
        @Test
        void rejectsMissingTargetDate() {
            // act & assert
            assertThatThrownBy(() -> jobLauncherTestUtils.launchJob(new JobParameters()))
                .isInstanceOf(JobParametersInvalidException.class)
                .hasMessageContaining("targetDate");
        }

        @DisplayName("targetDate가 유효한 yyyyMMdd 날짜가 아니면 Job을 시작하지 않는다")
        @ValueSource(strings = {"2099-07-14", "20990230", "hello"})
        @ParameterizedTest
        void rejectsInvalidTargetDate(String targetDate) {
            // arrange
            JobParameters jobParameters = jobParameters(targetDate);

            // act & assert
            assertThatThrownBy(() -> jobLauncherTestUtils.launchJob(jobParameters))
                .isInstanceOf(JobParametersInvalidException.class)
                .hasMessageContaining("targetDate");
        }
    }

    private JobParameters jobParameters(String targetDate) {
        return new JobParametersBuilder()
            .addString("targetDate", targetDate)
            .toJobParameters();
    }

    private Double score(String key, String member) {
        return masterRedisTemplate.opsForZSet().score(key, member);
    }
}
