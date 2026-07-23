package com.loopers.ranking.infrastructure;

import com.loopers.ranking.RankingPeriod;
import com.loopers.ranking.RankingScorePolicy;
import com.loopers.ranking.application.NewProductRankingSnapshot;
import com.loopers.ranking.application.ProductRankingAssignment;
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

    @DisplayName("DAILY 후보 저장과 순위 부여는 지원하지 않는다.")
    @Test
    void rejectsDailyPeriod() {
        // act & assert
        assertThatThrownBy(() -> candidateRepository.upsertAll(
            RankingPeriod.DAILY,
            List.of(new RankingCandidate(1L, 101L, 5.3))
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("DAILY");
        assertThatThrownBy(() -> candidateRepository.assignRanks(
            RankingPeriod.DAILY,
            1L,
            List.of()
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("DAILY");
        assertThatThrownBy(() -> candidateRepository.deleteUnrankedCandidates(
            RankingPeriod.DAILY,
            1L,
            1_000
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("DAILY");
    }

    @DisplayName("DB 정렬 순서를 유지해 동점 상품에 결정적인 순위를 저장하고 재조회한다.")
    @EnumSource(value = RankingPeriod.class, names = {"WEEKLY", "MONTHLY"})
    @ParameterizedTest
    void assignsAndFindsRankedProducts(RankingPeriod period) {
        // arrange
        long snapshotId = insertSnapshot(period);
        candidateRepository.upsertAll(period, List.of(
            new RankingCandidate(snapshotId, 402L, 5.3),
            new RankingCandidate(snapshotId, 303L, 10.0),
            new RankingCandidate(snapshotId, 401L, 5.3)
        ));

        // act
        List<RankingCandidate> topCandidates =
            candidateRepository.findTopCandidates(period, snapshotId, 100);
        List<ProductRankingAssignment> assignments = List.of(
            new ProductRankingAssignment(303L, 10.0, 1),
            new ProductRankingAssignment(401L, 5.3, 2),
            new ProductRankingAssignment(402L, 5.3, 3)
        );
        candidateRepository.assignRanks(period, snapshotId, assignments);
        List<ProductRankingAssignment> rankedProducts =
            candidateRepository.findRankedProducts(period, snapshotId, 101);

        // assert
        assertAll(
            () -> assertThat(candidateRepository.countCandidates(period, snapshotId))
                .isEqualTo(3),
            () -> assertThat(topCandidates).containsExactly(
                new RankingCandidate(snapshotId, 303L, 10.0),
                new RankingCandidate(snapshotId, 401L, 5.3),
                new RankingCandidate(snapshotId, 402L, 5.3)
            ),
            () -> assertThat(rankedProducts).containsExactlyElementsOf(assignments)
        );
    }

    @DisplayName("대상 Snapshot의 탈락 후보만 제한된 수만큼 상품 ID 순서로 삭제한다.")
    @EnumSource(value = RankingPeriod.class, names = {"WEEKLY", "MONTHLY"})
    @ParameterizedTest
    void deletesOnlyLimitedUnrankedCandidates(RankingPeriod period) {
        // arrange
        long snapshotId = insertSnapshot(period);
        candidateRepository.upsertAll(period, List.of(
            new RankingCandidate(snapshotId, 101L, 10.0),
            new RankingCandidate(snapshotId, 202L, 7.0),
            new RankingCandidate(snapshotId, 303L, 5.0),
            new RankingCandidate(snapshotId, 404L, 3.0)
        ));
        candidateRepository.assignRanks(
            period,
            snapshotId,
            List.of(new ProductRankingAssignment(101L, 10.0, 1))
        );

        // act
        int firstDeleted = candidateRepository.deleteUnrankedCandidates(
            period,
            snapshotId,
            2
        );
        List<Long> productIdsAfterFirstDelete = jdbcTemplate.queryForList(
            "select product_id from " + tableName(period)
                + " where snapshot_id = ? order by product_id",
            Long.class,
            snapshotId
        );
        int secondDeleted = candidateRepository.deleteUnrankedCandidates(
            period,
            snapshotId,
            2
        );
        int thirdDeleted = candidateRepository.deleteUnrankedCandidates(
            period,
            snapshotId,
            2
        );

        // assert
        List<Long> remainingProductIds = jdbcTemplate.queryForList(
            "select product_id from " + tableName(period)
                + " where snapshot_id = ? order by product_id",
            Long.class,
            snapshotId
        );
        assertAll(
            () -> assertThat(firstDeleted).isEqualTo(2),
            () -> assertThat(productIdsAfterFirstDelete)
                .containsExactly(101L, 404L),
            () -> assertThat(secondDeleted).isEqualTo(1),
            () -> assertThat(thirdDeleted).isZero(),
            () -> assertThat(remainingProductIds).containsExactly(101L),
            () -> assertThat(candidateRepository.findRankedProducts(
                period,
                snapshotId,
                101
            )).containsExactly(new ProductRankingAssignment(101L, 10.0, 1))
        );
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
