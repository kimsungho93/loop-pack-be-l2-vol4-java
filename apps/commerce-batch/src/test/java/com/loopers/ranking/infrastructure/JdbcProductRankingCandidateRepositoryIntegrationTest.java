package com.loopers.ranking.infrastructure;

import com.loopers.ranking.RankingPeriod;
import com.loopers.ranking.RankingScorePolicy;
import com.loopers.ranking.application.NewProductRankingSnapshot;
import com.loopers.ranking.application.ProductRankingSnapshotKey;
import com.loopers.ranking.application.RankingCandidate;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(properties = "spring.batch.job.enabled=false")
class JdbcProductRankingCandidateRepositoryIntegrationTest {

    private static final RankingScorePolicy SCORE_POLICY =
        new RankingScorePolicy(0.1, 0.2, 0.7, 10_000);

    private final JdbcProductRankingCandidateRepository candidateRepository;
    private final JdbcProductRankingSnapshotRepository snapshotRepository;
    private final JdbcTemplate jdbcTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    JdbcProductRankingCandidateRepositoryIntegrationTest(
        JdbcProductRankingCandidateRepository candidateRepository,
        JdbcProductRankingSnapshotRepository snapshotRepository,
        JdbcTemplate jdbcTemplate,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.candidateRepository = candidateRepository;
        this.snapshotRepository = snapshotRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("기간에 맞는 MV에 후보를 절대값으로 UPSERT하고 기존 순위를 초기화한다.")
    @EnumSource(value = RankingPeriod.class, names = {"WEEKLY", "MONTHLY"})
    @ParameterizedTest
    void upsertsAbsoluteScoreAndClearsRank(RankingPeriod period) {
        // arrange
        long snapshotId = insertSnapshot(period);
        RankingCandidate initial = new RankingCandidate(snapshotId, 101L, 5.3);
        candidateRepository.upsertAll(period, List.of(initial));
        jdbcTemplate.update(
            "update " + tableName(period) + " set rank_no = 1 where snapshot_id = ?",
            snapshotId
        );

        // act
        candidateRepository.upsertAll(
            period,
            List.of(new RankingCandidate(snapshotId, 101L, 7.7))
        );

        // assert
        CandidateRow result = jdbcTemplate.queryForObject(
            "select product_id, rank_no, score from " + tableName(period)
                + " where snapshot_id = ?",
            (resultSet, rowNumber) -> new CandidateRow(
                resultSet.getLong("product_id"),
                resultSet.getObject("rank_no", Integer.class),
                resultSet.getDouble("score")
            ),
            snapshotId
        );
        Long otherTableCount = jdbcTemplate.queryForObject(
            "select count(*) from " + otherTableName(period),
            Long.class
        );
        assertAll(
            () -> assertThat(result.productId()).isEqualTo(101L),
            () -> assertThat(result.rankNo()).isNull(),
            () -> assertThat(result.score()).isEqualTo(7.7),
            () -> assertThat(otherTableCount).isZero()
        );
    }

    @DisplayName("DAILY 후보 저장은 지원하지 않는다.")
    @Test
    void rejectsDailyPeriod() {
        // act & assert
        assertThatThrownBy(() -> candidateRepository.upsertAll(
            RankingPeriod.DAILY,
            List.of(new RankingCandidate(1L, 101L, 5.3))
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("DAILY");
    }

    private long insertSnapshot(RankingPeriod period) {
        ProductRankingSnapshotKey key = new ProductRankingSnapshotKey(
            period,
            LocalDate.of(2026, 7, 19),
            1
        );
        snapshotRepository.insert(
            new NewProductRankingSnapshot(
                key,
                SCORE_POLICY,
                Instant.parse("2026-07-20T02:00:00Z")
            )
        );
        return snapshotRepository.findBy(key).orElseThrow().id();
    }

    private String tableName(RankingPeriod period) {
        return switch (period) {
            case WEEKLY -> "mv_product_rank_weekly";
            case MONTHLY -> "mv_product_rank_monthly";
            case DAILY -> throw new IllegalArgumentException("DAILY is not supported");
        };
    }

    private String otherTableName(RankingPeriod period) {
        return switch (period) {
            case WEEKLY -> "mv_product_rank_monthly";
            case MONTHLY -> "mv_product_rank_weekly";
            case DAILY -> throw new IllegalArgumentException("DAILY is not supported");
        };
    }

    private record CandidateRow(long productId, Integer rankNo, double score) {
    }
}
