package com.loopers.batch.job.ranking.step;

import com.loopers.batch.job.ranking.RankingRebuildJobConfig;
import com.loopers.batch.job.ranking.RankingRebuildJobParameters;
import com.loopers.ranking.application.RankingRebuildService;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = RankingRebuildJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Component
public class RankingRebuildTasklet implements Tasklet {

    private final RankingRebuildService rankingRebuildService;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        JobExecution jobExecution = contribution.getStepExecution().getJobExecution();
        RankingRebuildJobParameters parameters = RankingRebuildJobParameters.from(
            jobExecution.getJobParameters()
        );
        rankingRebuildService.rebuild(parameters.rankingDate(), jobExecution.getId());
        return RepeatStatus.FINISHED;
    }
}
