package com.loopers.ranking.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RankingColdStartPropertiesTest {

    @DisplayName("Carry-Over 비율이 0 이하이거나 1을 초과하면 생성할 수 없다")
    @ValueSource(doubles = {-0.1, 0.0, 1.1})
    @ParameterizedTest
    void rejectsCarryOverRateOutsideValidRange(double carryOverRate) {
        // act & assert
        assertThatThrownBy(() -> new RankingColdStartProperties(carryOverRate))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
