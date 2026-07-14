package com.loopers.batch.job.ranking.step;

import com.loopers.batch.job.ranking.RankingCarryOverJobConfig;
import com.loopers.batch.job.ranking.RankingCarryOverJobParameters;
import com.loopers.ranking.application.RankingCarryOverService;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = RankingCarryOverJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Component
public class RankingCarryOverTasklet implements Tasklet {

    private final RankingCarryOverService rankingCarryOverService;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        JobParameters jobParameters = contribution.getStepExecution()
            .getJobExecution()
            .getJobParameters();
        RankingCarryOverJobParameters parameters = RankingCarryOverJobParameters.from(jobParameters);
        rankingCarryOverService.carryOver(parameters.targetDate());
        return RepeatStatus.FINISHED;
    }
}
