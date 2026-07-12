package com.loopers.queue.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class QueuePropertiesTest {

    @DisplayName("폴링 간격을 계산할 때 ")
    @Nested
    class PollAfterSeconds {

        @DisplayName("예상 대기 시간에 비례 계수를 곱한 값을 반환한다.")
        @Test
        void returnsRatioOfEstimatedWait() {
            // arrange — jitter 없이 결정적 계산: 60초 × 0.15 = 9초
            QueueProperties.Poll poll = new QueueProperties.Poll(0.15, 3L, 30L, 0);

            // act & assert
            assertThat(poll.pollAfterSeconds(60L)).isEqualTo(9L);
        }

        @DisplayName("계산값이 최대치를 넘으면, 최대치로 자른다.")
        @Test
        void clampsToMax_whenCalculatedValueExceedsMax() {
            // arrange — 400초 × 0.15 = 60초 → 최대 30초
            QueueProperties.Poll poll = new QueueProperties.Poll(0.15, 3L, 30L, 0);

            // act & assert
            assertThat(poll.pollAfterSeconds(400L)).isEqualTo(30L);
        }

        @DisplayName("계산값이 최소치보다 작으면, 최소치로 올린다.")
        @Test
        void clampsToMin_whenCalculatedValueIsBelowMin() {
            // arrange — 10초 × 0.15 = 1.5초 → 최소 3초
            QueueProperties.Poll poll = new QueueProperties.Poll(0.15, 3L, 30L, 0);

            // act & assert
            assertThat(poll.pollAfterSeconds(10L)).isEqualTo(3L);
        }

        @DisplayName("예상 대기 시간이 0이어도, 최소치를 반환한다.")
        @Test
        void returnsMin_whenEstimatedWaitIsZero() {
            // arrange
            QueueProperties.Poll poll = new QueueProperties.Poll(0.15, 3L, 30L, 10);

            // act & assert
            assertThat(poll.pollAfterSeconds(0L)).isEqualTo(3L);
        }

        @DisplayName("jitter가 있으면, 기준값의 ±비율 범위 안에서 분산된 값을 반환한다.")
        @Test
        void spreadsWithinJitterRange_whenJitterIsSet() {
            // arrange — 기준 9초 ±10% → [8.1, 9.9] → 반올림 [8, 10]
            QueueProperties.Poll poll = new QueueProperties.Poll(0.15, 3L, 30L, 10);
            Set<Long> observed = new HashSet<>();

            // act
            for (int i = 0; i < 100; i++) {
                long interval = poll.pollAfterSeconds(60L);
                assertThat(interval).isBetween(8L, 10L);
                observed.add(interval);
            }

            // assert — 전부 같은 값이면 분산이 아니다.
            assertThat(observed.size()).isGreaterThan(1);
        }

        @DisplayName("jitter를 더해도, 최소/최대 범위를 벗어나지 않는다.")
        @Test
        void staysWithinBounds_whenJitterPushesOverMax() {
            // arrange — 기준 60초는 이미 최대 초과 → jitter 와 무관하게 항상 30초
            QueueProperties.Poll poll = new QueueProperties.Poll(0.15, 3L, 30L, 10);

            // act & assert
            for (int i = 0; i < 100; i++) {
                assertThat(poll.pollAfterSeconds(400L)).isEqualTo(30L);
            }
        }
    }
}
