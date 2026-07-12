package com.loopers.metrics.application;

public record EventHandlingMetadata(
    String topicName,
    int partitionNo,
    long offsetNo
) {
}
