package com.loopers.queue.application;

import com.loopers.queue.domain.QueueEntryStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QueueFacadeTest {

    // batchSize 10, 100ms 간격 → 초당 100명 처리. 폴링 정책은 jitter 0으로 결정적.
    private final QueueProperties properties =
        new QueueProperties(10, 100L, Duration.ofMinutes(5), new QueueProperties.Poll(0.15, 3L, 30L, 0));
    private final WaitingQueue waitingQueue = mock(WaitingQueue.class);
    private final QueueFacade queueFacade = new QueueFacade(waitingQueue, properties);

    @DisplayName("대기열에 진입할 때 ")
    @Nested
    class Enter {

        @DisplayName("WAITING 상태와 배정된 순번을 반환한다.")
        @Test
        void returnsWaitingInfoWithPosition() {
            // arrange
            when(waitingQueue.enter(eq(101L), anyLong())).thenReturn(new QueueEnterResult(3L, 10L));

            // act
            QueuePositionInfo info = queueFacade.enter(101L);

            // assert
            assertAll(
                () -> assertThat(info.status()).isEqualTo(QueueEntryStatus.WAITING),
                () -> assertThat(info.position()).isEqualTo(3L),
                () -> assertThat(info.totalWaiting()).isEqualTo(10L)
            );
        }
    }

    @DisplayName("대기 상태를 조회할 때 ")
    @Nested
    class GetPosition {

        @DisplayName("줄에 서 있으면, WAITING 상태와 순번을 반환한다.")
        @Test
        void returnsWaiting_whenUserIsInQueue() {
            // arrange
            when(waitingQueue.findRank(101L)).thenReturn(Optional.of(2L));
            when(waitingQueue.countWaiting()).thenReturn(10L);

            // act
            QueuePositionInfo info = queueFacade.getPosition(101L);

            // assert
            assertAll(
                () -> assertThat(info.status()).isEqualTo(QueueEntryStatus.WAITING),
                () -> assertThat(info.position()).isEqualTo(3L),
                () -> assertThat(info.totalWaiting()).isEqualTo(10L)
            );
        }

        @DisplayName("줄에 없고 토큰이 있으면, READY 상태와 토큰을 반환한다.")
        @Test
        void returnsReadyWithToken_whenUserIsAdmitted() {
            // arrange
            when(waitingQueue.findRank(101L)).thenReturn(Optional.empty());
            when(waitingQueue.findToken(101L)).thenReturn(Optional.of("token-a"));

            // act
            QueuePositionInfo info = queueFacade.getPosition(101L);

            // assert
            assertAll(
                () -> assertThat(info.status()).isEqualTo(QueueEntryStatus.READY),
                () -> assertThat(info.token()).isEqualTo("token-a")
            );
        }

        @DisplayName("줄에도 없고 토큰도 없으면, EXPIRED 상태를 반환한다.")
        @Test
        void returnsExpired_whenUserIsNowhere() {
            // arrange
            when(waitingQueue.findRank(101L)).thenReturn(Optional.empty());
            when(waitingQueue.findToken(101L)).thenReturn(Optional.empty());
            when(waitingQueue.isTokenUsed(101L)).thenReturn(false);

            // act
            QueuePositionInfo info = queueFacade.getPosition(101L);

            // assert
            assertThat(info.status()).isEqualTo(QueueEntryStatus.EXPIRED);
        }

        @DisplayName("줄에 없고 토큰이 사용된 상태면, COMPLETED 상태를 반환한다.")
        @Test
        void returnsCompleted_whenTokenWasUsed() {
            // arrange
            when(waitingQueue.findRank(101L)).thenReturn(Optional.empty());
            when(waitingQueue.findToken(101L)).thenReturn(Optional.empty());
            when(waitingQueue.isTokenUsed(101L)).thenReturn(true);

            // act
            QueuePositionInfo info = queueFacade.getPosition(101L);

            // assert
            assertThat(info.status()).isEqualTo(QueueEntryStatus.COMPLETED);
        }
    }
}
