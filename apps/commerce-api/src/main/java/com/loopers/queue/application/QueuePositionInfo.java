package com.loopers.queue.application;

import com.loopers.queue.domain.QueueEntryStatus;

public record QueuePositionInfo(
    QueueEntryStatus status,
    Long position,
    Long totalWaiting,
    Long estimatedWaitSeconds,
    Long pollAfterSeconds,
    String token
) {

    public static QueuePositionInfo waiting(long position, long totalWaiting, double permitsPerSecond, QueueProperties.Poll poll) {
        long estimatedWaitSeconds = (long) Math.ceil(position / permitsPerSecond);
        return new QueuePositionInfo(
            QueueEntryStatus.WAITING,
            position,
            totalWaiting,
            estimatedWaitSeconds,
            poll.pollAfterSeconds(estimatedWaitSeconds),
            null
        );
    }

    public static QueuePositionInfo ready(String token) {
        return new QueuePositionInfo(QueueEntryStatus.READY, null, null, null, null, token);
    }

    public static QueuePositionInfo completed() {
        return new QueuePositionInfo(QueueEntryStatus.COMPLETED, null, null, null, null, null);
    }

    public static QueuePositionInfo expired() {
        return new QueuePositionInfo(QueueEntryStatus.EXPIRED, null, null, null, null, null);
    }
}
