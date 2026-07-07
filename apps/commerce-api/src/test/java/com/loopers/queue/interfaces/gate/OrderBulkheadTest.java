package com.loopers.queue.interfaces.gate;

import com.loopers.queue.application.QueueProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class OrderBulkheadTest {

    private OrderBulkhead bulkheadOf(int limit) {
        return new OrderBulkhead(new QueueProperties(
            5, 100L, Duration.ofMinutes(5), Duration.ofMinutes(30), limit, new QueueProperties.Poll(0.15, 3L, 30L, 0)));
    }

    @DisplayName("상한만큼만 동시 진입을 허용하고, 초과분은 거절한다.")
    @Test
    void allowsUpToLimit_andRejectsBeyond() {
        // arrange
        OrderBulkhead bulkhead = bulkheadOf(2);

        // act & assert
        assertAll(
            () -> assertThat(bulkhead.tryEnter()).isTrue(),
            () -> assertThat(bulkhead.tryEnter()).isTrue(),
            () -> assertThat(bulkhead.tryEnter()).isFalse()
        );
    }

    @DisplayName("빠져나가면, 그 자리만큼 다시 진입할 수 있다.")
    @Test
    void allowsReentry_afterExit() {
        // arrange
        OrderBulkhead bulkhead = bulkheadOf(1);
        bulkhead.tryEnter();

        // act
        bulkhead.exit();

        // assert
        assertThat(bulkhead.tryEnter()).isTrue();
    }
}
