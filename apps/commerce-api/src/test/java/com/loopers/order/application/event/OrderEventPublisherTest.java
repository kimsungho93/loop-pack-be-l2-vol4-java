package com.loopers.order.application.event;

import com.loopers.order.application.OrderInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderEventPublisherTest {

    private static final Long USER_ID = 1L;
    private static final Long ORDER_ID = 1_351_039_135L;
    private static final ZonedDateTime OCCURRED_AT = ZonedDateTime.parse("2026-06-25T10:00:05+09:00");

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @DisplayName("주문 이벤트를 발행할 때")
    @Nested
    class Publish {

        @DisplayName("주문 생성 이벤트는 주문 정보 기반으로 발행된다.")
        @Test
        void publishesOrderCreatedEvent() {
            // arrange
            OrderInfo order = createOrderInfo();
            OrderEventPublisher publisher = new OrderEventPublisher(applicationEventPublisher);

            // act
            publisher.publishCreated(order, OCCURRED_AT);

            // assert
            ArgumentCaptor<OrderCreatedEvent> eventCaptor = ArgumentCaptor.forClass(OrderCreatedEvent.class);
            verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
            OrderCreatedEvent event = eventCaptor.getValue();

            assertAll(
                () -> assertThat(event.orderId()).isEqualTo(ORDER_ID),
                () -> assertThat(event.userId()).isEqualTo(USER_ID),
                () -> assertThat(event.orderTotalPrice()).isEqualTo(3_100_000L),
                () -> assertThat(event.paymentAmount()).isEqualTo(3_100_000L),
                () -> assertThat(event.itemCount()).isEqualTo(1),
                () -> assertThat(event.occurredAt()).isEqualTo(OCCURRED_AT)
            );
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
