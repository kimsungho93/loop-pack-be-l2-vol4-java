package com.loopers.queue.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QueueAdmitterTest {

    private final QueueProperties properties = new QueueProperties(
        10, 100L, Duration.ofMinutes(5), Duration.ofMinutes(30), new QueueProperties.Poll(0.15, 3L, 30L, 0));
    private final WaitingQueue waitingQueue = mock(WaitingQueue.class);
    private final QueueMetrics queueMetrics = mock(QueueMetrics.class);
    private final QueueAdmitter queueAdmitter = new QueueAdmitter(waitingQueue, properties, queueMetrics);

    @DisplayName("다음 배치를 입장시키면, 배치 크기만큼 서로 다른 토큰을 만들어 전달하고 입장 인원을 지표로 남긴다.")
    @Test
    void admitsWithDistinctTokensOfBatchSize() {
        // arrange
        when(waitingQueue.admit(anyList(), eq(Duration.ofMinutes(5)))).thenReturn(3);

        // act
        int admitted = queueAdmitter.admitNextBatch();

        // assert
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> tokensCaptor = ArgumentCaptor.forClass(List.class);
        verify(waitingQueue).admit(tokensCaptor.capture(), eq(Duration.ofMinutes(5)));
        List<String> tokens = tokensCaptor.getValue();
        assertAll(
            () -> assertThat(admitted).isEqualTo(3),
            () -> assertThat(tokens).hasSize(10),
            () -> assertThat(new HashSet<>(tokens)).hasSize(10),
            () -> verify(queueMetrics).recordAdmitted(3)
        );
    }
}
