package com.loopers.queue.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class QueueMetrics {

    private final Counter admittedCounter;
    private final Counter tokenConsumedCounter;
    private final Counter tokenRestoredCounter;
    private final Counter gateFailOpenCounter;
    private final Counter schedulerRunCounter;
    private final Timer waitTimeTimer;
    private final AtomicLong schedulerLastRunAtMillis = new AtomicLong(System.currentTimeMillis());

    public QueueMetrics(MeterRegistry meterRegistry, WaitingQueue waitingQueue) {
        Gauge.builder("queue.waiting.depth", waitingQueue, WaitingQueue::countWaiting)
            .description("현재 대기열에 서 있는 인원 수")
            .register(meterRegistry);
        Gauge.builder("queue.tokens.active", waitingQueue, WaitingQueue::countActiveTokens)
            .description("아직 소비·만료되지 않은 유효 입장 토큰 수 (이론 최대 = 발급률 × TTL)")
            .register(meterRegistry);
        this.admittedCounter = Counter.builder("queue.admitted.total")
            .description("입장 토큰을 발급받은 누적 인원 수")
            .register(meterRegistry);
        this.tokenConsumedCounter = Counter.builder("queue.token.consumed.total")
            .description("게이트에서 소비된 입장 토큰 누적 수 (복구된 소비 포함)")
            .register(meterRegistry);
        this.tokenRestoredCounter = Counter.builder("queue.token.restored.total")
            .description("주문 실패로 복구된 입장 토큰 누적 수")
            .register(meterRegistry);
        this.gateFailOpenCounter = Counter.builder("queue.gate.failopen.total")
            .description("게이트 판정 불가로 fail-open 통과한 누적 요청 수 (Redis 장애 신호)")
            .register(meterRegistry);
        this.schedulerRunCounter = Counter.builder("queue.scheduler.runs.total")
            .description("입장 스케줄러 실행 누적 횟수 — rate 하락은 tick 지연의 전조")
            .register(meterRegistry);
        this.waitTimeTimer = Timer.builder("queue.wait.time")
            .description("진입부터 입장까지 실제 대기 시간 분포")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry);
        // 죽은 스케줄러는 자기 죽음을 보고할 수 없으므로, "마지막 실행이 얼마나 오래됐나"를
        // 스크랩 시점에 계산한다(dead man's switch). 값이 커지는 살아있는 숫자라 no-data 함정이 없다.
        Gauge.builder("queue.scheduler.last.run.age.seconds",
                () -> (System.currentTimeMillis() - schedulerLastRunAtMillis.get()) / 1000.0)
            .description("입장 스케줄러 마지막 실행 후 경과 시간 — 주기(100ms) 대비 커지면 사망 신호")
            .register(meterRegistry);
    }

    /** 입장 인원 수와 함께 각자의 실제 대기 시간(진입→입장)을 분포로 남긴다. */
    public void recordAdmitted(List<Long> waitedMillis) {
        admittedCounter.increment(waitedMillis.size());
        waitedMillis.forEach(millis -> waitTimeTimer.record(Duration.ofMillis(millis)));
    }

    /** 스케줄러 심장박동 — 실행 횟수와 마지막 실행 시각을 남긴다. age 게이지가 이 시각으로 생존을 판정한다. */
    public void recordSchedulerRun() {
        schedulerRunCounter.increment();
        schedulerLastRunAtMillis.set(System.currentTimeMillis());
    }

    public void recordTokenConsumed() {
        tokenConsumedCounter.increment();
    }

    public void recordTokenRestored() {
        tokenRestoredCounter.increment();
    }

    /** 게이트 저장소 판정 불가로 fail-open 통과가 발생 — 오르기 시작하면 Redis 장애 신호이므로 알람 대상. */
    public void recordGateFailOpen() {
        gateFailOpenCounter.increment();
    }
}
