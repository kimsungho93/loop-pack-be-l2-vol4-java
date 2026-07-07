package com.loopers.queue.application;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mock;

class QueueMetricsTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final QueueMetrics queueMetrics = new QueueMetrics(meterRegistry, mock(WaitingQueue.class));

    @DisplayName("스케줄러 실행을 기록하면, 실행 횟수가 오르고 마지막 실행 나이가 0 근처로 돌아간다.")
    @Test
    void recordsHeartbeat_whenSchedulerRuns() {
        // act
        queueMetrics.recordSchedulerRun();

        // assert — age 게이지는 "지금 - 마지막 실행"을 조회 시점에 계산한다.
        double runs = meterRegistry.get("queue.scheduler.runs.total").counter().count();
        double ageSeconds = meterRegistry.get("queue.scheduler.last.run.age.seconds").gauge().value();
        assertAll(
            () -> assertThat(runs).isEqualTo(1.0),
            () -> assertThat(ageSeconds).isBetween(0.0, 1.0)
        );
    }

    @DisplayName("입장 기록에 대기 시간이 담기면, 입장 인원 수와 대기 시간 분포를 함께 남긴다.")
    @Test
    void recordsWaitTimes_whenUsersAreAdmitted() {
        // act — 두 명이 각각 10초, 20초 기다렸다 입장
        queueMetrics.recordAdmitted(List.of(10_000L, 20_000L));

        // assert
        double admitted = meterRegistry.get("queue.admitted.total").counter().count();
        long waitSamples = meterRegistry.get("queue.wait.time").timer().count();
        assertAll(
            () -> assertThat(admitted).isEqualTo(2.0),
            () -> assertThat(waitSamples).isEqualTo(2L),
            () -> assertThat(meterRegistry.get("queue.wait.time").timer().totalTime(java.util.concurrent.TimeUnit.SECONDS))
                .isEqualTo(30.0)
        );
    }
}
