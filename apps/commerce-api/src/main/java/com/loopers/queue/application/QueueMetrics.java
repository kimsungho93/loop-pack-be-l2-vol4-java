package com.loopers.queue.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class QueueMetrics {

    private final Counter admittedCounter;
    private final Counter tokenConsumedCounter;
    private final Counter tokenRestoredCounter;

    public QueueMetrics(MeterRegistry meterRegistry, WaitingQueue waitingQueue) {
        Gauge.builder("queue.waiting.depth", waitingQueue, WaitingQueue::countWaiting)
            .description("현재 대기열에 서 있는 인원 수")
            .register(meterRegistry);
        this.admittedCounter = Counter.builder("queue.admitted.total")
            .description("입장 토큰을 발급받은 누적 인원 수")
            .register(meterRegistry);
        this.tokenConsumedCounter = Counter.builder("queue.token.consumed.total")
            .description("주문에 사용된 입장 토큰 누적 수")
            .register(meterRegistry);
        this.tokenRestoredCounter = Counter.builder("queue.token.restored.total")
            .description("주문 실패로 복구된 입장 토큰 누적 수")
            .register(meterRegistry);
    }

    public void recordAdmitted(int admitted) {
        admittedCounter.increment(admitted);
    }

    public void recordTokenConsumed() {
        tokenConsumedCounter.increment();
    }

    public void recordTokenRestored() {
        tokenRestoredCounter.increment();
    }
}
