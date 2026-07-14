package com.loopers.product.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class ProductDetailMetrics {

    private final Counter rankingLookupFailureCounter;

    public ProductDetailMetrics(MeterRegistry meterRegistry) {
        this.rankingLookupFailureCounter = Counter.builder("product.detail.ranking.lookup.failures.total")
            .description("상품 상세 조회 중 오늘의 Ranking 조회에 실패한 누적 횟수")
            .register(meterRegistry);
    }

    public void recordRankingLookupFailure() {
        rankingLookupFailureCounter.increment();
    }
}
