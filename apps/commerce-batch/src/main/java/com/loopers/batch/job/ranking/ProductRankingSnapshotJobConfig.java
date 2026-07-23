package com.loopers.batch.job.ranking;

import com.loopers.batch.job.ranking.step.PrepareProductRankingTasklet;
import com.loopers.batch.listener.StepMonitorListener;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@ConditionalOnProperty(
    name = "spring.batch.job.name",
    havingValue = ProductRankingSnapshotJobConfig.JOB_NAME
)
@RequiredArgsConstructor
@Configuration
public class ProductRankingSnapshotJobConfig {

    public static final String JOB_NAME = "productRankingSnapshotJob";
    public static final String PREPARE_STEP_NAME = "prepareProductRankingStep";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final StepMonitorListener stepMonitorListener;
    private final PrepareProductRankingTasklet tasklet;

    @Bean(PREPARE_STEP_NAME)
    public Step prepareProductRankingStep() {
        return new StepBuilder(PREPARE_STEP_NAME, jobRepository)
            .tasklet(tasklet, transactionManager)
            .listener(stepMonitorListener)
            .build();
    }
}
