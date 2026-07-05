package com.loopers.queue.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

@RequiredArgsConstructor
@Component
public class QueueAdmitter {

    private final WaitingQueue waitingQueue;
    private final QueueProperties queueProperties;
    private final QueueMetrics queueMetrics;

    public int admitNextBatch() {
        List<String> tokens = IntStream.range(0, queueProperties.admitBatchSize())
            .mapToObj(i -> UUID.randomUUID().toString())
            .toList();
        int admitted = waitingQueue.admit(tokens, queueProperties.tokenTtl());
        queueMetrics.recordAdmitted(admitted);
        return admitted;
    }
}
