package com.loopers.queue.application;

import com.loopers.queue.domain.QueueEntryStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class QueuePositionInfoTest {

    // jitter 0 — 단위 테스트에서 결정적 계산을 위해
    private static final QueueProperties.Poll POLL = new QueueProperties.Poll(0.15, 3L, 30L, 0);

    @DisplayName("대기 중 정보를 만들 때 ")
    @Nested
    class Waiting {

        @DisplayName("WAITING 상태와 순번, 전체 대기 인원을 담는다.")
        @Test
        void containsWaitingStatusAndPosition() {
            // act
            QueuePositionInfo info = QueuePositionInfo.waiting(3L, 10L, 100.0, POLL);

            // assert
            assertAll(
                () -> assertThat(info.status()).isEqualTo(QueueEntryStatus.WAITING),
                () -> assertThat(info.position()).isEqualTo(3L),
                () -> assertThat(info.totalWaiting()).isEqualTo(10L),
                () -> assertThat(info.token()).isNull()
            );
        }

        @DisplayName("예상 대기 시간은 순번을 초당 처리량으로 나눠 올림한 값이다.")
        @Test
        void estimatesWaitSecondsByRoundingUp() {
            // act
            QueuePositionInfo justOverOneSecond = QueuePositionInfo.waiting(150L, 200L, 100.0, POLL);
            QueuePositionInfo exactlyOneSecond = QueuePositionInfo.waiting(100L, 200L, 100.0, POLL);

            // assert
            assertAll(
                () -> assertThat(justOverOneSecond.estimatedWaitSeconds()).isEqualTo(2L),
                () -> assertThat(exactlyOneSecond.estimatedWaitSeconds()).isEqualTo(1L)
            );
        }

        @DisplayName("다음 폴링 간격을 예상 대기 시간 기반으로 담는다.")
        @Test
        void containsPollAfterSecondsBasedOnEstimatedWait() {
            // act — 순번 6,000 ÷ 초당 100명 = 예상 60초 → 60 × 0.15 = 9초
            QueuePositionInfo info = QueuePositionInfo.waiting(6_000L, 10_000L, 100.0, POLL);

            // assert
            assertThat(info.pollAfterSeconds()).isEqualTo(9L);
        }
    }

    @DisplayName("입장 완료 정보를 만들 때 ")
    @Nested
    class Ready {

        @DisplayName("READY 상태와 토큰만 담는다.")
        @Test
        void containsReadyStatusAndToken() {
            // act
            QueuePositionInfo info = QueuePositionInfo.ready("token-a");

            // assert — 폴링을 중단해야 하므로 다음 간격도 없어야 한다.
            assertAll(
                () -> assertThat(info.status()).isEqualTo(QueueEntryStatus.READY),
                () -> assertThat(info.token()).isEqualTo("token-a"),
                () -> assertThat(info.position()).isNull(),
                () -> assertThat(info.estimatedWaitSeconds()).isNull(),
                () -> assertThat(info.pollAfterSeconds()).isNull()
            );
        }
    }

    @DisplayName("만료 정보를 만들 때 ")
    @Nested
    class Expired {

        @DisplayName("EXPIRED 상태만 담는다.")
        @Test
        void containsExpiredStatusOnly() {
            // act
            QueuePositionInfo info = QueuePositionInfo.expired();

            // assert
            assertAll(
                () -> assertThat(info.status()).isEqualTo(QueueEntryStatus.EXPIRED),
                () -> assertThat(info.position()).isNull(),
                () -> assertThat(info.token()).isNull(),
                () -> assertThat(info.pollAfterSeconds()).isNull()
            );
        }
    }
}
