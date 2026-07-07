package com.loopers.queue.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@RequiredArgsConstructor
@Component
public class QueueFacade {

    private final WaitingQueue waitingQueue;
    private final QueueProperties queueProperties;

    public QueueEnterInfo enter(long userId) {
        QueueEnterResult result = waitingQueue.enter(userId, System.currentTimeMillis());
        String waitingToken = UUID.randomUUID().toString();
        waitingQueue.saveWaitingToken(waitingToken, userId, queueProperties.waitingTokenTtl());
        QueuePositionInfo position = QueuePositionInfo.waiting(
            result.position(), result.totalWaiting(), queueProperties.permitsPerSecond(), queueProperties.poll());
        return new QueueEnterInfo(waitingToken, position);
    }

    public QueuePositionInfo getPositionByWaitingToken(String waitingToken) {
        return waitingQueue.findUserIdByWaitingToken(waitingToken)
            .map(this::getPosition)
            .orElseGet(QueuePositionInfo::expired);
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
