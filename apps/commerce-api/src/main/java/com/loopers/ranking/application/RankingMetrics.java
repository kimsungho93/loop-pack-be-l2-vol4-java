package com.loopers.ranking.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class RankingMetrics {

    private final Counter pageLookupFailureCounter;

    public RankingMetrics(MeterRegistry meterRegistry) {
        this.pageLookupFailureCounter = Counter.builder("ranking.page.lookup.failures.total")
            .description("일간 Ranking Page 조회에 실패한 누적 횟수")
            .register(meterRegistry);
    }

    public void recordPageLookupFailure() {
        pageLookupFailureCounter.increment();
    }
}
