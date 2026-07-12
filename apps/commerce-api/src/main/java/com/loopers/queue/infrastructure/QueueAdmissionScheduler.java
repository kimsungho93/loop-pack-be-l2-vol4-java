package com.loopers.queue.infrastructure;

import com.loopers.queue.application.QueueAdmitter;
import com.loopers.queue.application.QueueMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@ConditionalOnProperty(
    name = "commerce.queue.admit-enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class QueueAdmissionScheduler {

    private final QueueAdmitter queueAdmitter;
    private final QueueMetrics queueMetrics;

    // fixedRate 는 지연 발생 시 밀린 실행이 한꺼번에 몰릴 수 있어 fixedDelay 를 사용한다.
    @Scheduled(fixedDelayString = "${commerce.queue.admit-fixed-delay-ms:100}")
    public void admit() {
        queueAdmitter.admitNextBatch();
        // 심장박동은 성공한 tick 에만 찍는다 — admit 이 계속 실패하면 age 가 자라 사망과 같은 신호가 된다.
        queueMetrics.recordSchedulerRun();
    }
}
