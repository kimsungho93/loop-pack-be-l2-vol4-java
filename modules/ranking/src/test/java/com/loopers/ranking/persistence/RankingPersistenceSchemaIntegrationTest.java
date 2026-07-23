package com.loopers.ranking.persistence;

import com.loopers.RankingPersistenceTestApplication;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
    classes = RankingPersistenceTestApplication.class,
    properties = "spring.config.import=classpath:jpa.yml"
)
class RankingPersistenceSchemaIntegrationTest {

    private static final LocalDate PERIOD_START = LocalDate.of(2026, 7, 1);
    private static final LocalDate AGGREGATION_END_DATE = LocalDate.of(2026, 7, 20);

    private final JdbcTemplate jdbcTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    RankingPersistenceSchemaIntegrationTest(
        JdbcTemplate jdbcTemplate,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("공유 Ranking 스키마를 생성할 때")
    @Nested
    class CreateSchema {

        @DisplayName("Snapshot과 주간·월간 Ranking 테이블을 생성한다")
        @Test
        void createsSnapshotAndPublishedRankingTables() {
            // act
            List<String> tableNames = jdbcTemplate.queryForList(
                """
                    select table_name
                    from information_schema.tables
                    where table_schema = database()
                      and table_name in (
                          'product_rank_snapshots',
                          'mv_product_rank_weekly',
                          'mv_product_rank_monthly'
                      )
                    order by table_name
                    """,
                String.class
            );

            // assert
            assertThat(tableNames).containsExactly(
                "mv_product_rank_monthly",
                "mv_product_rank_weekly",
                "product_rank_snapshots"
            );
        }

        @DisplayName("의미를 식별할 수 있는 이름으로 Unique, Check, FK 제약조건을 생성한다")
        @Test
        void createsNamedConstraints() {
            // act
            List<String> constraintNames = jdbcTemplate.queryForList(
                """
                    select constraint_name
                    from information_schema.table_constraints
                    where table_schema = database()
                      and table_name in (
                          'product_rank_snapshots',
                          'mv_product_rank_weekly',
                          'mv_product_rank_monthly'
                      )
                      and constraint_type in ('UNIQUE', 'CHECK', 'FOREIGN KEY')
                    order by constraint_name
                    """,
                String.class
            );

            // assert
            assertThat(constraintNames).contains(
                "uk_product_rank_snapshots_period_aggregation_end_date_revision",
                "ck_product_rank_snapshots_period",
                "ck_product_rank_snapshots_revision_positive",
                "ck_product_rank_snapshots_date_range",
                "ck_product_rank_snapshots_order_amount_unit_positive",
                "uk_mv_product_rank_weekly_snapshot_id_rank_no",
                "fk_mv_product_rank_weekly_snapshot_id",
                "ck_mv_product_rank_weekly_rank_no_range",
                "uk_mv_product_rank_monthly_snapshot_id_rank_no",
                "fk_mv_product_rank_monthly_snapshot_id",
                "ck_mv_product_rank_monthly_rank_no_range"
            );
        }

        @DisplayName("주간·월간 Ranking의 기본 키는 Snapshot과 상품 순서로 구성한다")
        @ValueSource(strings = {"mv_product_rank_weekly", "mv_product_rank_monthly"})
        @ParameterizedTest
        void createsSnapshotLeadingCompositePrimaryKey(String tableName) {
            // act
            List<String> primaryKeyColumns = jdbcTemplate.queryForList(
                """
                    select column_name
                    from information_schema.key_column_usage
                    where table_schema = database()
                      and table_name = ?
                      and constraint_name = 'PRIMARY'
                    order by ordinal_position
                    """,
                String.class,
                tableName
            );

            // assert
            assertThat(primaryKeyColumns).containsExactly("snapshot_id", "product_id");
        }

        @DisplayName("Snapshot과 주간·월간 Ranking의 핵심 컬럼 타입과 null 허용 범위를 생성한다")
        @Test
        void createsRequiredColumnContracts() {
            // act
            List<ColumnSchema> columns = jdbcTemplate.query(
                """
                    select table_name, column_name, data_type, is_nullable, datetime_precision
                    from information_schema.columns
                    where table_schema = database()
                      and table_name in (
                          'product_rank_snapshots',
                          'mv_product_rank_weekly',
                          'mv_product_rank_monthly'
                      )
                    """,
                (resultSet, rowNumber) -> new ColumnSchema(
                    resultSet.getString("table_name"),
                    resultSet.getString("column_name"),
                    resultSet.getString("data_type"),
                    resultSet.getString("is_nullable"),
                    resultSet.getObject("datetime_precision", Integer.class)
                )
            );

            // assert
            assertThat(columns).contains(
                new ColumnSchema("product_rank_snapshots", "id", "bigint", "NO", null),
                new ColumnSchema("product_rank_snapshots", "period", "varchar", "NO", null),
                new ColumnSchema("product_rank_snapshots", "period_start", "date", "NO", null),
                new ColumnSchema("product_rank_snapshots", "aggregation_end_date", "date", "NO", null),
                new ColumnSchema("product_rank_snapshots", "revision", "int", "NO", null),
                new ColumnSchema("product_rank_snapshots", "score_policy_version", "varchar", "NO", null),
                new ColumnSchema("product_rank_snapshots", "view_weight", "double", "NO", null),
                new ColumnSchema("product_rank_snapshots", "like_weight", "double", "NO", null),
                new ColumnSchema("product_rank_snapshots", "order_weight", "double", "NO", null),
                new ColumnSchema("product_rank_snapshots", "order_amount_unit", "bigint", "NO", null),
                new ColumnSchema("product_rank_snapshots", "created_at", "datetime", "NO", 6),
                new ColumnSchema("product_rank_snapshots", "completed_at", "datetime", "YES", 6),
                new ColumnSchema("mv_product_rank_weekly", "snapshot_id", "bigint", "NO", null),
                new ColumnSchema("mv_product_rank_weekly", "product_id", "bigint", "NO", null),
                new ColumnSchema("mv_product_rank_weekly", "rank_no", "int", "YES", null),
                new ColumnSchema("mv_product_rank_weekly", "score", "double", "NO", null),
                new ColumnSchema("mv_product_rank_monthly", "snapshot_id", "bigint", "NO", null),
                new ColumnSchema("mv_product_rank_monthly", "product_id", "bigint", "NO", null),
                new ColumnSchema("mv_product_rank_monthly", "rank_no", "int", "YES", null),
                new ColumnSchema("mv_product_rank_monthly", "score", "double", "NO", null)
            );
        }

        @DisplayName("Unique 제약조건을 설계한 컬럼 순서로 생성한다")
        @CsvSource({
            "product_rank_snapshots, uk_product_rank_snapshots_period_aggregation_end_date_revision, period|aggregation_end_date|revision",
            "mv_product_rank_weekly, uk_mv_product_rank_weekly_snapshot_id_rank_no, snapshot_id|rank_no",
            "mv_product_rank_monthly, uk_mv_product_rank_monthly_snapshot_id_rank_no, snapshot_id|rank_no"
        })
        @ParameterizedTest
        void createsUniqueConstraintColumns(String tableName, String constraintName, String expectedColumns) {
            // act
            List<String> uniqueColumns = constraintColumns(tableName, constraintName);

            // assert
            assertThat(uniqueColumns).containsExactly(expectedColumns.split("\\|"));
        }

        @DisplayName("주간·월간 Ranking FK의 삭제 규칙은 RESTRICT다")
        @ValueSource(strings = {
            "fk_mv_product_rank_weekly_snapshot_id",
            "fk_mv_product_rank_monthly_snapshot_id"
        })
        @ParameterizedTest
        void createsRestrictForeignKey(String constraintName) {
            // act
            String deleteRule = jdbcTemplate.queryForObject(
                """
                    select delete_rule
                    from information_schema.referential_constraints
                    where constraint_schema = database()
                      and constraint_name = ?
                    """,
                String.class,
                constraintName
            );

            // assert
            assertThat(deleteRule).isEqualTo("RESTRICT");
        }
    }

    @DisplayName("Ranking Snapshot을 저장할 때")
    @Nested
    class SaveSnapshot {

        @DisplayName("같은 기간과 집계 종료일 및 revision은 한 번만 저장한다")
        @Test
        void rejectsDuplicateBusinessKey() {
            // arrange
            insertSnapshot("WEEKLY", PERIOD_START, AGGREGATION_END_DATE, 1, 10_000);

            // act & assert
            assertThatThrownBy(
                () -> insertSnapshot("WEEKLY", PERIOD_START, AGGREGATION_END_DATE, 1, 10_000)
            ).isInstanceOf(DataAccessException.class);
        }

        @DisplayName("같은 집계 종료일과 revision이라도 기간이 다르면 각각 저장한다")
        @Test
        void allowsSameDateAndRevisionForDifferentPeriods() {
            // act
            insertSnapshot("WEEKLY", PERIOD_START, AGGREGATION_END_DATE, 1, 10_000);
            insertSnapshot("MONTHLY", PERIOD_START, AGGREGATION_END_DATE, 1, 10_000);

            // assert
            Integer count = jdbcTemplate.queryForObject(
                """
                    select count(*)
                    from product_rank_snapshots
                    where aggregation_end_date = ?
                      and revision = ?
                    """,
                Integer.class,
                AGGREGATION_END_DATE,
                1
            );
            assertThat(count).isEqualTo(2);
        }

        @DisplayName("지원하지 않는 기간은 저장하지 않는다")
        @Test
        void rejectsUnsupportedPeriod() {
            // act & assert
            assertThatThrownBy(
                () -> insertSnapshot("DAILY", PERIOD_START, AGGREGATION_END_DATE, 1, 10_000)
            ).isInstanceOf(DataAccessException.class);
        }

        @DisplayName("revision은 1 이상이어야 한다")
        @Test
        void rejectsNonPositiveRevision() {
            // act & assert
            assertThatThrownBy(
                () -> insertSnapshot("WEEKLY", PERIOD_START, AGGREGATION_END_DATE, 0, 10_000)
            ).isInstanceOf(DataAccessException.class);
        }

        @DisplayName("기간 시작일은 집계 종료일보다 늦을 수 없다")
        @Test
        void rejectsReversedDateRange() {
            // act & assert
            assertThatThrownBy(
                () -> insertSnapshot("WEEKLY", AGGREGATION_END_DATE, PERIOD_START, 1, 10_000)
            ).isInstanceOf(DataAccessException.class);
        }

        @DisplayName("주문 금액 단위는 0보다 커야 한다")
        @Test
        void rejectsNonPositiveOrderAmountUnit() {
            // act & assert
            assertThatThrownBy(
                () -> insertSnapshot("WEEKLY", PERIOD_START, AGGREGATION_END_DATE, 1, 0)
            ).isInstanceOf(DataAccessException.class);
        }
    }

    @DisplayName("주간·월간 Ranking 후보를 저장할 때")
    @Nested
    class SaveRankingCandidate {

        @DisplayName("계산 중인 여러 후보는 rankNo 없이 저장할 수 있다")
        @CsvSource({
            "mv_product_rank_weekly, WEEKLY",
            "mv_product_rank_monthly, MONTHLY"
        })
        @ParameterizedTest
        void allowsMultipleCandidatesWithoutRank(String tableName, String period) {
            // arrange
            long snapshotId = insertSnapshot(period, PERIOD_START, AGGREGATION_END_DATE, 1, 10_000);

            // act
            insertCandidate(tableName, snapshotId, 101L, null, 12.5);
            insertCandidate(tableName, snapshotId, 202L, null, 11.5);

            // assert
            Integer count = jdbcTemplate.queryForObject(
                "select count(*) from " + tableName + " where snapshot_id = ? and rank_no is null",
                Integer.class,
                snapshotId
            );
            assertThat(count).isEqualTo(2);
        }

        @DisplayName("같은 Snapshot에 같은 실제 순위를 중복 저장할 수 없다")
        @CsvSource({
            "mv_product_rank_weekly, WEEKLY",
            "mv_product_rank_monthly, MONTHLY"
        })
        @ParameterizedTest
        void rejectsDuplicatePublishedRank(String tableName, String period) {
            // arrange
            long snapshotId = insertSnapshot(period, PERIOD_START, AGGREGATION_END_DATE, 1, 10_000);
            insertCandidate(tableName, snapshotId, 101L, 1, 12.5);

            // act & assert
            assertThatThrownBy(
                () -> insertCandidate(tableName, snapshotId, 202L, 1, 11.5)
            ).isInstanceOf(DataAccessException.class);
        }

        @DisplayName("실제 순위는 1부터 100까지만 저장한다")
        @CsvSource({
            "mv_product_rank_weekly, WEEKLY, 0",
            "mv_product_rank_weekly, WEEKLY, 101",
            "mv_product_rank_monthly, MONTHLY, 0",
            "mv_product_rank_monthly, MONTHLY, 101"
        })
        @ParameterizedTest
        void rejectsRankOutsideTopOneHundred(String tableName, String period, int rankNo) {
            // arrange
            long snapshotId = insertSnapshot(period, PERIOD_START, AGGREGATION_END_DATE, 1, 10_000);

            // act & assert
            assertThatThrownBy(
                () -> insertCandidate(tableName, snapshotId, 101L, rankNo, 12.5)
            ).isInstanceOf(DataAccessException.class);
        }

        @DisplayName("존재하지 않는 Snapshot의 후보는 저장할 수 없다")
        @ValueSource(strings = {"mv_product_rank_weekly", "mv_product_rank_monthly"})
        @ParameterizedTest
        void rejectsMissingSnapshot(String tableName) {
            // act & assert
            assertThatThrownBy(
                () -> insertCandidate(tableName, 999L, 101L, null, 12.5)
            ).isInstanceOf(DataAccessException.class);
        }

        @DisplayName("후보가 남아 있는 Snapshot은 먼저 삭제할 수 없다")
        @CsvSource({
            "mv_product_rank_weekly, WEEKLY",
            "mv_product_rank_monthly, MONTHLY"
        })
        @ParameterizedTest
        void preventsDeletingReferencedSnapshot(String tableName, String period) {
            // arrange
            long snapshotId = insertSnapshot(period, PERIOD_START, AGGREGATION_END_DATE, 1, 10_000);
            insertCandidate(tableName, snapshotId, 101L, null, 12.5);

            // act & assert
            assertThatThrownBy(
                () -> jdbcTemplate.update("delete from product_rank_snapshots where id = ?", snapshotId)
            ).isInstanceOf(DataAccessException.class);
        }
    }

    private long insertSnapshot(
        String period,
        LocalDate periodStart,
        LocalDate aggregationEndDate,
        int revision,
        long orderAmountUnit
    ) {
        jdbcTemplate.update(
            """
                insert into product_rank_snapshots(
                    period,
                    period_start,
                    aggregation_end_date,
                    revision,
                    score_policy_version,
                    view_weight,
                    like_weight,
                    order_weight,
                    order_amount_unit,
                    created_at,
                    completed_at
                )
                values (?, ?, ?, ?, 'V1', 0.1, 0.2, 0.7, ?, ?, null)
                """,
            period,
            periodStart,
            aggregationEndDate,
            revision,
            orderAmountUnit,
            LocalDateTime.of(2026, 7, 21, 2, 0)
        );
        return jdbcTemplate.queryForObject(
            """
                select id
                from product_rank_snapshots
                where period = ?
                  and aggregation_end_date = ?
                  and revision = ?
                """,
            Long.class,
            period,
            aggregationEndDate,
            revision
        );
    }

    private void insertCandidate(
        String tableName,
        long snapshotId,
        long productId,
        Integer rankNo,
        double score
    ) {
        jdbcTemplate.update(
            "insert into " + tableName + "(snapshot_id, product_id, rank_no, score) values (?, ?, ?, ?)",
            snapshotId,
            productId,
            rankNo,
            score
        );
    }

    private List<String> constraintColumns(String tableName, String constraintName) {
        return jdbcTemplate.queryForList(
            """
                select column_name
                from information_schema.key_column_usage
                where table_schema = database()
                  and table_name = ?
                  and constraint_name = ?
                order by ordinal_position
                """,
            String.class,
            tableName,
            constraintName
        );
    }

    private record ColumnSchema(
        String tableName,
        String columnName,
        String dataType,
        String nullable,
        Integer datetimePrecision
    ) {
    }
}
