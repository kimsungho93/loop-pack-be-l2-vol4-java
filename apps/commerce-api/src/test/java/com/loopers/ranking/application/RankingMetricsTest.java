package com.loopers.ranking.application;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RankingMetricsTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final RankingMetrics rankingMetrics = new RankingMetrics(meterRegistry);

    @DisplayName("Ranking Page 조회 실패를 기록하면 실패 Counter가 증가한다")
    @Test
    void incrementsFailureCounter_whenPageLookupFails() {
        // act
        rankingMetrics.recordPageLookupFailure();

        // assert
        assertThat(meterRegistry.get("ranking.page.lookup.failures.total").counter().count())
            .isEqualTo(1.0);
    }
}
