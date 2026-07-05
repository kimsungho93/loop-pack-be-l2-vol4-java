package com.loopers.queue.infrastructure;

import com.loopers.queue.application.QueueAdmitter;
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

    // fixedRate 는 지연 발생 시 밀린 실행이 한꺼번에 몰릴 수 있어 fixedDelay 를 사용한다.
    @Scheduled(fixedDelayString = "${commerce.queue.admit-fixed-delay-ms:100}")
    public void admit() {
        queueAdmitter.admitNextBatch();
    }
}
