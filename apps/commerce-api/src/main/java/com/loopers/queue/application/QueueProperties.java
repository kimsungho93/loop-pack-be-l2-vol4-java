package com.loopers.queue.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * 입장 속도(100ms 간격 × 5명 = 초당 50명) 산정 근거 — 2026-07-05 로컬 부하 실측 기반:
 * - POST /api/v1/orders 실측: 순차 평균 80ms(요청당 BCrypt 인증 ~72ms + 주문 트랜잭션 ~8ms),
 *   동시 40 스레드에서 처리량 95 req/s 로 포화(에러 0건).
 * - 병목은 DB 커넥션 풀(40개, 평균 점유 1개 미만)이 아니라 요청마다 수행하는 BCrypt 인증의 CPU 비용이다.
 * - 실측 포화 처리량 95 req/s 에 안전마진 50%를 적용해 초당 50명 → 100ms 간격 × 5명.
 * - 최악의 동시 유효 토큰 수 = 초당 입장 인원(50) × 토큰 TTL(300s) = 15,000개.
 */
@ConfigurationProperties("commerce.queue")
public record QueueProperties(
    @DefaultValue("5") int admitBatchSize,
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
