package com.loopers.batch.job.ranking.step;

import com.loopers.ranking.application.RankingRebuildService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.repeat.RepeatStatus;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RankingRebuildTaskletTest {

    private static final LocalDate RANKING_DATE = LocalDate.of(2099, 7, 14);
    private static final long JOB_EXECUTION_ID = 42L;

    @Mock
    private RankingRebuildService rankingRebuildService;

    @Mock
    private StepContribution contribution;

    @Mock
    private StepExecution stepExecution;

    @Mock
    private JobExecution jobExecution;

    private RankingRebuildTasklet tasklet;

    @BeforeEach
    void setUp() {
        tasklet = new RankingRebuildTasklet(rankingRebuildService);
    }

    @DisplayName("rankingDate와 JobExecution ID로 일간 랭킹을 Rebuild한다")
    @Test
    void rebuildsRankingWithRankingDateAndJobExecutionId() {
        // arrange
        JobParameters jobParameters = new JobParametersBuilder()
            .addString("rankingDate", "20990714")
            .addLong("run.id", 1L)
            .toJobParameters();
        when(contribution.getStepExecution()).thenReturn(stepExecution);
        when(stepExecution.getJobExecution()).thenReturn(jobExecution);
        when(jobExecution.getJobParameters()).thenReturn(jobParameters);
        when(jobExecution.getId()).thenReturn(JOB_EXECUTION_ID);

        // act
        RepeatStatus result = tasklet.execute(contribution, null);

        // assert
        assertAll(
            () -> verify(rankingRebuildService).rebuild(RANKING_DATE, JOB_EXECUTION_ID),
            () -> assertThat(result).isEqualTo(RepeatStatus.FINISHED)
        );
    }
}
