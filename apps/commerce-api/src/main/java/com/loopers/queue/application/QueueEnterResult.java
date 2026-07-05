package com.loopers.queue.application;

public record QueueEnterResult(
    long position,
    long totalWaiting
) {
}
