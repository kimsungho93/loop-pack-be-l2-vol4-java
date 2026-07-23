package com.loopers.ranking.infrastructure;

import com.loopers.ranking.RankingPeriod;
import com.loopers.ranking.application.ProductRankingAssignment;
import com.loopers.ranking.application.ProductRankingCandidateRepository;
import com.loopers.ranking.application.RankingCandidate;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class JdbcProductRankingCandidateRepository
    implements ProductRankingCandidateRepository {

    private static final String WEEKLY_UPSERT_SQL = """
        insert into mv_product_rank_weekly(
            snapshot_id,
            product_id,
            rank_no,
            score
        )
        values (?, ?, null, ?)
        on duplicate key update
            score = ?,
            rank_no = null
        """;
    private static final String MONTHLY_UPSERT_SQL = """
        insert into mv_product_rank_monthly(
            snapshot_id,
            product_id,
            rank_no,
            score
        )
        values (?, ?, null, ?)
        on duplicate key update
            score = ?,
            rank_no = null
        """;
    private static final CandidateQueries WEEKLY_QUERIES = new CandidateQueries(
        """
            select count(*)
            from mv_product_rank_weekly
            where snapshot_id = ?
            """,
        """
            select product_id, score
            from mv_product_rank_weekly
            where snapshot_id = ?
            order by score desc, product_id asc
            limit ?
            """,
        """
            update mv_product_rank_weekly
            set rank_no = ?
            where snapshot_id = ?
              and product_id = ?
              and rank_no is null
            """,
        """
            select product_id, score, rank_no
            from mv_product_rank_weekly
            where snapshot_id = ?
              and rank_no is not null
            order by rank_no asc
            limit ?
            """,
        """
            delete from mv_product_rank_weekly
            where snapshot_id = ?
              and rank_no is null
            order by product_id
            limit ?
            """
    );
    private static final CandidateQueries MONTHLY_QUERIES = new CandidateQueries(
        """
            select count(*)
            from mv_product_rank_monthly
            where snapshot_id = ?
            """,
        """
            select product_id, score
            from mv_product_rank_monthly
            where snapshot_id = ?
            order by score desc, product_id asc
            limit ?
            """,
        """
            update mv_product_rank_monthly
            set rank_no = ?
            where snapshot_id = ?
              and product_id = ?
              and rank_no is null
            """,
        """
            select product_id, score, rank_no
            from mv_product_rank_monthly
            where snapshot_id = ?
              and rank_no is not null
            order by rank_no asc
            limit ?
            """,
        """
            delete from mv_product_rank_monthly
            where snapshot_id = ?
              and rank_no is null
            order by product_id
            limit ?
            """
    );

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void upsertAll(
        RankingPeriod period,
        List<? extends RankingCandidate> candidates
    ) {
        String sql = switch (period) {
            case WEEKLY -> WEEKLY_UPSERT_SQL;
            case MONTHLY -> MONTHLY_UPSERT_SQL;
            case DAILY -> throw new IllegalArgumentException(
                "DAILY product ranking candidates are not supported"
            );
        };
        if (candidates.isEmpty()) {
            return;
        }

        List<Object[]> batchArguments = candidates.stream()
            .map(candidate -> new Object[]{
                candidate.snapshotId(),
                candidate.productId(),
                candidate.score(),
                candidate.score()
            })
            .toList();
        jdbcTemplate.batchUpdate(sql, batchArguments);
    }

    @Override
    public long countCandidates(RankingPeriod period, long snapshotId) {
        Long count = jdbcTemplate.queryForObject(
            queries(period).countSql(),
            Long.class,
            snapshotId
        );
        if (count == null) {
            throw new IllegalStateException("product ranking candidate count is missing");
        }
        return count;
    }

    @Override
    public List<RankingCandidate> findTopCandidates(
        RankingPeriod period,
        long snapshotId,
        int limit
    ) {
        return jdbcTemplate.query(
            queries(period).topCandidatesSql(),
            (resultSet, rowNumber) -> new RankingCandidate(
                snapshotId,
                resultSet.getLong("product_id"),
                resultSet.getDouble("score")
            ),
            snapshotId,
            limit
        );
    }

    @Override
    public void assignRanks(
        RankingPeriod period,
        long snapshotId,
        List<ProductRankingAssignment> assignments
    ) {
        CandidateQueries candidateQueries = queries(period);
        if (assignments.isEmpty()) {
            return;
        }

        List<Object[]> batchArguments = assignments.stream()
            .map(assignment -> new Object[]{
                assignment.rankNo(),
                snapshotId,
                assignment.productId()
            })
            .toList();
        jdbcTemplate.batchUpdate(candidateQueries.assignRanksSql(), batchArguments);
    }

    @Override
    public List<ProductRankingAssignment> findRankedProducts(
        RankingPeriod period,
        long snapshotId,
        int limit
    ) {
        return jdbcTemplate.query(
            queries(period).rankedProductsSql(),
            (resultSet, rowNumber) -> new ProductRankingAssignment(
                resultSet.getLong("product_id"),
                resultSet.getDouble("score"),
                resultSet.getInt("rank_no")
            ),
            snapshotId,
            limit
        );
    }

    @Override
    public int deleteUnrankedCandidates(
        RankingPeriod period,
        long snapshotId,
        int limit
    ) {
        return jdbcTemplate.update(
            queries(period).deleteUnrankedCandidatesSql(),
            snapshotId,
            limit
        );
    }

    private CandidateQueries queries(RankingPeriod period) {
        return switch (period) {
            case WEEKLY -> WEEKLY_QUERIES;
            case MONTHLY -> MONTHLY_QUERIES;
            case DAILY -> throw new IllegalArgumentException(
                "DAILY product ranking candidates are not supported"
            );
        };
    }

    private record CandidateQueries(
        String countSql,
        String topCandidatesSql,
        String assignRanksSql,
        String rankedProductsSql,
        String deleteUnrankedCandidatesSql
    ) {
    }
}
