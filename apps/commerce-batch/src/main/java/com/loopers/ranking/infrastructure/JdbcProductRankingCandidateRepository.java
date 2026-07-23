package com.loopers.ranking.infrastructure;

import com.loopers.ranking.RankingPeriod;
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
}
