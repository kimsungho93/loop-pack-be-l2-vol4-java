package com.loopers.order.application.listener;

import com.loopers.order.application.OrderInfo;
import com.loopers.order.application.event.OrderCreatedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class OrderActivityLogListenerTest {

    private static final Long USER_ID = 1L;
    private static final Long ORDER_ID = 1_351_039_135L;
    private static final ZonedDateTime OCCURRED_AT = ZonedDateTime.parse("2026-06-25T10:00:05+09:00");

    private final OrderActivityLogListener listener = new OrderActivityLogListener();

    @DisplayName("주문 행동 로그를 남길 때")
    @Nested
    class Record {

        @DisplayName("주문 생성 이벤트를 서버 로그로 남긴다.")
        @Test
        void recordsOrderCreatedActivity(CapturedOutput output) {
            // arrange
            OrderCreatedEvent event = OrderCreatedEvent.from(createOrderInfo(), OCCURRED_AT);

            // act
            listener.recordOrderCreated(event);

            // assert
            assertThat(output).contains("Order created activity recorded");
            assertThat(output).contains("orderId=1351039135");
            assertThat(output).contains("userId=1");
        }
    }

    private OrderInfo createOrderInfo() {
        return new OrderInfo(
            ORDER_ID,
            USER_ID,
            null,
            3_100_000L,
            0L,
            3_100_000L,
            List.of(new OrderInfo.Item(1L, "Apple", 101L, "iPhone 16 Pro", 1_550_000L, 2, 3_100_000L)),
            OCCURRED_AT,
            OCCURRED_AT
        );
    }
}
