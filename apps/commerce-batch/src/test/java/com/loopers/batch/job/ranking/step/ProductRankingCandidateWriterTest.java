package com.loopers.batch.job.ranking.step;

import com.loopers.ranking.RankingPeriod;
import com.loopers.ranking.application.ProductRankingCandidateRepository;
import com.loopers.ranking.application.RankingCandidate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.item.Chunk;

import java.util.List;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProductRankingCandidateWriterTest {

    @Mock
    private ProductRankingCandidateRepository candidateRepository;

    @DisplayName("실행 기간과 Chunk 후보를 저장소에 전달한다.")
    @Test
    void writesCandidatesForExecutionPeriod() {
        // arrange
        ProductRankingCandidateWriter writer = new ProductRankingCandidateWriter(
            candidateRepository,
            "WEEKLY",
            "20260719",
            1L
        );
        List<RankingCandidate> candidates = List.of(
            new RankingCandidate(10L, 101L, 5.3),
            new RankingCandidate(10L, 202L, 3.0)
        );

        // act
        writer.write(new Chunk<>(candidates));

        // assert
        verify(candidateRepository).upsertAll(RankingPeriod.WEEKLY, candidates);
    }
}
