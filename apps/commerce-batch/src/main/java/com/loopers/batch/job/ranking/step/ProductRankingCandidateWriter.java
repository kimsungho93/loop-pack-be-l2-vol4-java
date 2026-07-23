package com.loopers.batch.job.ranking.step;

import com.loopers.batch.job.ranking.ProductRankingSnapshotJobConfig;
import com.loopers.batch.job.ranking.ProductRankingSnapshotJobParameters;
import com.loopers.ranking.RankingPeriod;
import com.loopers.ranking.application.ProductRankingCandidateRepository;
import com.loopers.ranking.application.RankingCandidate;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@StepScope
@ConditionalOnProperty(
    name = "spring.batch.job.name",
    havingValue = ProductRankingSnapshotJobConfig.JOB_NAME
)
@Component
public class ProductRankingCandidateWriter implements ItemWriter<RankingCandidate> {

    private final ProductRankingCandidateRepository candidateRepository;
    private final RankingPeriod period;

    public ProductRankingCandidateWriter(
        ProductRankingCandidateRepository candidateRepository,
        @Value("#{jobParameters['period']}") String period,
        @Value("#{jobParameters['aggregationEndDate']}") String aggregationEndDate,
        @Value("#{jobParameters['revision']}") Long revision
    ) {
        this.candidateRepository = candidateRepository;
        this.period = ProductRankingSnapshotJobParameters
            .from(period, aggregationEndDate, revision)
            .period();
    }

    @Override
    public void write(Chunk<? extends RankingCandidate> chunk) {
        candidateRepository.upsertAll(period, chunk.getItems());
    }
}
