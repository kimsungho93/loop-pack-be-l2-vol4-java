package com.loopers.queue.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class QueueFacade {

    private final WaitingQueue waitingQueue;
    private final QueueProperties queueProperties;

    public QueuePositionInfo enter(long userId) {
        QueueEnterResult result = waitingQueue.enter(userId, System.currentTimeMillis());
        return QueuePositionInfo.waiting(result.position(), result.totalWaiting(), queueProperties.permitsPerSecond(), queueProperties.poll());
    }

    public QueuePositionInfo getPosition(long userId) {
        return waitingQueue.findRank(userId)
            .map(rank -> QueuePositionInfo.waiting(rank + 1, waitingQueue.countWaiting(), queueProperties.permitsPerSecond(), queueProperties.poll()))
            .orElseGet(() -> waitingQueue.findToken(userId)
                .map(QueuePositionInfo::ready)
                .orElseGet(() -> waitingQueue.isTokenUsed(userId)
                    ? QueuePositionInfo.completed()
                    : QueuePositionInfo.expired()));
    }
}
