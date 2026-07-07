package com.loopers.queue.application;

public record QueueEnterInfo(
    String waitingToken,
    QueuePositionInfo position
) {
}
