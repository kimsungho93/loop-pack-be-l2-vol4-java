package com.loopers.job.ranking;

import com.loopers.batch.job.ranking.RankingCarryOverJobConfig;
import com.loopers.ranking.application.RankingCarryOverRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = {
    "spring.batch.job.name=" + RankingCarryOverJobConfig.JOB_NAME,
    "spring.batch.job.enabled=false"
})
class RankingCarryOverJobConcurrencyE2ETest {

    private static final LocalDate SOURCE_DATE = LocalDate.of(2099, 7, 13);
    private static final LocalDate TARGET_DATE = LocalDate.of(2099, 7, 14);
    private static final double CARRY_OVER_RATE = 0.1;

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private JobRepositoryTestUtils jobRepositoryTestUtils;

    @Autowired
    @Qualifier(RankingCarryOverJobConfig.JOB_NAME)
    private Job job;

    @MockitoBean
    private RankingCarryOverRepository repository;

    @BeforeEach
    void setUp() {
        jobLauncherTestUtils.setJob(job);
    }

    @AfterEach
    void tearDown() {
        jobRepositoryTestUtils.removeJobExecutions();
    }

    @DisplayName("같은 targetDate Job이 실행 중이면 중복 실행을 거부한다")
    @Test
    void rejectsConcurrentLaunch_forSameTargetDate() throws Exception {
        // arrange
        CountDownLatch repositoryEntered = new CountDownLatch(1);
        CountDownLatch releaseRepository = new CountDownLatch(1);
        doAnswer(invocation -> {
            repositoryEntered.countDown();
            if (!releaseRepository.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out while waiting to release Carry-Over Repository");
            }
            return 1L;
        }).when(repository).carryOver(SOURCE_DATE, TARGET_DATE, CARRY_OVER_RATE);
        JobParameters jobParameters = new JobParametersBuilder()
            .addString("targetDate", "20990714")
            .toJobParameters();
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<JobExecution> firstExecution = executor.submit(new Callable<>() {
                @Override
                public JobExecution call() throws Exception {
                    return jobLauncherTestUtils.launchJob(jobParameters);
                }
            });
            assertThat(repositoryEntered.await(5, TimeUnit.SECONDS)).isTrue();

            // act & assert
            assertThatThrownBy(() -> jobLauncherTestUtils.launchJob(jobParameters))
                .isInstanceOf(JobExecutionAlreadyRunningException.class);

            releaseRepository.countDown();
            assertThat(firstExecution.get(5, TimeUnit.SECONDS).getExitStatus())
                .isEqualTo(ExitStatus.COMPLETED);
            verify(repository).carryOver(SOURCE_DATE, TARGET_DATE, CARRY_OVER_RATE);
        } finally {
            releaseRepository.countDown();
            executor.shutdownNow();
        }
    }
}
