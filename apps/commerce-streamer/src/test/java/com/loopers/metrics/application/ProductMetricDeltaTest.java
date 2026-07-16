package com.loopers.metrics.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductMetricDeltaTest {

    @DisplayName("같은 상품의 지표 Delta를 항목별로 합산한다")
    @Test
    void addsMetricDeltasForSameProduct() {
        // arrange
        ProductMetricDelta first = new ProductMetricDelta(101L, 1, 2, 3);
        ProductMetricDelta second = new ProductMetricDelta(101L, -1, 4, 5);

        // act
        ProductMetricDelta result = first.plus(second);

        // assert
        assertThat(result).isEqualTo(new ProductMetricDelta(101L, 0, 6, 8));
    }

    @DisplayName("서로 다른 상품의 지표 Delta는 합산하지 않는다")
    @Test
    void rejectsMetricDeltasForDifferentProducts() {
        // arrange
        ProductMetricDelta first = new ProductMetricDelta(101L, 1, 2, 3);
        ProductMetricDelta second = new ProductMetricDelta(202L, -1, 4, 5);

        // act & assert
        assertThatThrownBy(() -> first.plus(second))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("productId");
    }
}
