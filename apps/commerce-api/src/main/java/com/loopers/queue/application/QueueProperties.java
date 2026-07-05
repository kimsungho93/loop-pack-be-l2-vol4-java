package com.loopers.queue.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * 입장 속도(100ms 간격 × 10명 = 초당 100명) 산정 근거:
 * - 주문 API 의 동시 처리 상한은 DB 커넥션 풀 40개가 결정한다(jpa.yml, test 프로필만 10개).
 * - 평균 주문 처리 시간을 50ms 로 가정하면(실측값 아님 — 부하 테스트로 보정 필요) 이론 처리량은 40 ÷ 0.05s = 초당 800건.
 * - 커넥션 풀은 주문 외 모든 API 가 공유하므로 대기열 입장에는 이론치의 약 1/8 만 배정해 초당 100명으로 잡았다.
 * - 최악의 동시 유효 토큰 수 = 초당 입장 인원(100) × 토큰 TTL(300s) = 30,000개.
 */
@ConfigurationProperties("commerce.queue")
public record QueueProperties(
    @DefaultValue("10") int admitBatchSize,
    @DefaultValue("100") long admitFixedDelayMs,
    @DefaultValue("5m") Duration tokenTtl
) {

    public QueueProperties {
        if (admitBatchSize <= 0) {
            throw new IllegalArgumentException("admitBatchSize must be positive.");
        }
        if (admitFixedDelayMs <= 0) {
            throw new IllegalArgumentException("admitFixedDelayMs must be positive.");
        }
        if (tokenTtl == null || tokenTtl.isZero() || tokenTtl.isNegative()) {
            throw new IllegalArgumentException("tokenTtl must be positive.");
        }
    }

    public double permitsPerSecond() {
        return admitBatchSize * 1000.0 / admitFixedDelayMs;
    }
}
