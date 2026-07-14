package com.loopers.batch.job.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RankingCarryOverJobParametersTest {

    @DisplayName("Carry-Over Job Parameter를 해석할 때")
    @Nested
    class Parse {

        @DisplayName("yyyyMMdd 형식의 targetDate를 날짜로 변환한다")
        @Test
        void parsesTargetDateInBasicIsoFormat() {
            // arrange
            JobParameters jobParameters = new JobParametersBuilder()
                .addString("targetDate", "20990714")
                .toJobParameters();

            // act
            RankingCarryOverJobParameters result = RankingCarryOverJobParameters.from(jobParameters);

            // assert
            assertThat(result.targetDate()).isEqualTo(LocalDate.of(2099, 7, 14));
        }

        @DisplayName("targetDate가 누락되면 실패한다")
        @Test
        void rejectsMissingTargetDate() {
            // arrange
            JobParameters jobParameters = new JobParameters();

            // act & assert
            assertThatThrownBy(() -> RankingCarryOverJobParameters.from(jobParameters))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("targetDate");
        }

        @DisplayName("targetDate가 yyyyMMdd 형식의 유효한 날짜가 아니면 실패한다")
        @ValueSource(strings = {"2099-07-14", "20990230", "hello"})
        @ParameterizedTest
        void rejectsInvalidTargetDate(String targetDate) {
            // arrange
            JobParameters jobParameters = new JobParametersBuilder()
                .addString("targetDate", targetDate)
                .toJobParameters();

            // act & assert
            assertThatThrownBy(() -> RankingCarryOverJobParameters.from(jobParameters))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("targetDate");
        }
    }
}
