package com.loopers.batch.job.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RankingRebuildJobParametersTest {

    @DisplayName("yyyyMMdd 형식의 rankingDate를 날짜로 변환한다")
    @Test
    void parsesRankingDateInBasicIsoFormat() {
        // arrange
        JobParameters jobParameters = new JobParametersBuilder()
            .addString("rankingDate", "20990714")
            .toJobParameters();

        // act
        RankingRebuildJobParameters result = RankingRebuildJobParameters.from(jobParameters);

        // assert
        assertThat(result.rankingDate()).isEqualTo(LocalDate.of(2099, 7, 14));
    }

    @DisplayName("rankingDate가 누락되면 실패한다")
    @Test
    void rejectsMissingRankingDate() {
        // arrange
        JobParameters jobParameters = new JobParameters();

        // act & assert
        assertThatThrownBy(() -> RankingRebuildJobParameters.from(jobParameters))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("rankingDate");
    }

    @DisplayName("rankingDate가 yyyyMMdd 형식의 유효한 날짜가 아니면 실패한다")
    @ValueSource(strings = {"2099-07-14", "20990230", "hello"})
    @ParameterizedTest
    void rejectsInvalidRankingDate(String rankingDate) {
        // arrange
        JobParameters jobParameters = new JobParametersBuilder()
            .addString("rankingDate", rankingDate)
            .toJobParameters();

        // act & assert
        assertThatThrownBy(() -> RankingRebuildJobParameters.from(jobParameters))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("rankingDate");
    }
}
