package com.loopers.payment.application.event;

import com.loopers.order.domain.Order;
import com.loopers.order.domain.OrderItem;
import com.loopers.order.domain.OrderItems;
import com.loopers.payment.domain.CardType;
import com.loopers.payment.domain.Payment;
import com.loopers.payment.domain.PaymentFailureReason;
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
class OrderPaymentEventPublisherTest {

    private static final Long USER_ID = 1L;
    private static final Long ORDER_ID = 1_351_039_135L;
    private static final long AMOUNT = 5_000L;
    private static final String CARD_NO = "1234-5678-9814-1451";
    private static final String TRANSACTION_KEY = "20250816:TR:9577c5";
    private static final ZonedDateTime REQUESTED_AT = ZonedDateTime.parse("2026-06-25T10:00:00+09:00");
    private static final ZonedDateTime OCCURRED_AT = ZonedDateTime.parse("2026-06-25T10:00:05+09:00");

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @DisplayName("주문 결제 이벤트를 발행할 때")
    @Nested
    class Publish {

        @DisplayName("결제 완료 이벤트는 결제 ID 기반의 결정적 이벤트 ID를 사용한다.")
        @Test
        void publishesOrderPaidEventWithDeterministicEventId() {
            // arrange
            Payment payment = createPendingPayment();
            payment.markSucceeded(TRANSACTION_KEY, "success", OCCURRED_AT);
            Order order = createOrder();
            OrderPaymentEventPublisher publisher = new OrderPaymentEventPublisher(applicationEventPublisher);

            // act
            publisher.publishPaid(payment, order, OCCURRED_AT);

            // assert
            ArgumentCaptor<OrderPaidEvent> eventCaptor = ArgumentCaptor.forClass(OrderPaidEvent.class);
            verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
            OrderPaidEvent event = eventCaptor.getValue();

            assertAll(
                () -> assertThat(event.eventId()).isEqualTo("payment:%s:paid".formatted(payment.getId())),
                () -> assertThat(event.orderId()).isEqualTo(ORDER_ID),
                () -> assertThat(event.paymentId()).isEqualTo(payment.getId()),
                () -> assertThat(event.userId()).isEqualTo(USER_ID),
                () -> assertThat(event.amount()).isEqualTo(AMOUNT),
                () -> assertThat(event.pgTransactionKey()).isEqualTo(TRANSACTION_KEY),
                () -> assertThat(event.items()).containsExactly(
                    new OrderPaidItemSnapshot(1L, AMOUNT, 1, AMOUNT)
                ),
                () -> assertThat(event.occurredAt()).isEqualTo(OCCURRED_AT)
            );
        }

        @DisplayName("결제 실패 이벤트는 결제 ID 기반의 결정적 이벤트 ID를 사용한다.")
        @Test
        void publishesOrderPaymentFailedEventWithDeterministicEventId() {
            // arrange
            Payment payment = createPendingPayment();
            payment.markFailed(
                TRANSACTION_KEY,
                PaymentFailureReason.LIMIT_EXCEEDED,
                "limit exceeded",
                OCCURRED_AT
            );
            OrderPaymentEventPublisher publisher = new OrderPaymentEventPublisher(applicationEventPublisher);

            // act
            publisher.publishFailed(payment, OCCURRED_AT);

            // assert
            ArgumentCaptor<OrderPaymentFailedEvent> eventCaptor = ArgumentCaptor.forClass(OrderPaymentFailedEvent.class);
            verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
            OrderPaymentFailedEvent event = eventCaptor.getValue();

            assertAll(
                () -> assertThat(event.eventId()).isEqualTo("payment:%s:failed".formatted(payment.getId())),
                () -> assertThat(event.orderId()).isEqualTo(ORDER_ID),
                () -> assertThat(event.paymentId()).isEqualTo(payment.getId()),
                () -> assertThat(event.userId()).isEqualTo(USER_ID),
                () -> assertThat(event.amount()).isEqualTo(AMOUNT),
                () -> assertThat(event.pgTransactionKey()).isEqualTo(TRANSACTION_KEY),
                () -> assertThat(event.failureReason()).isEqualTo(PaymentFailureReason.LIMIT_EXCEEDED),
                () -> assertThat(event.occurredAt()).isEqualTo(OCCURRED_AT)
            );
        }
    }

    private Payment createPendingPayment() {
        return Payment.pending(USER_ID, ORDER_ID, AMOUNT, CardType.SAMSUNG, CARD_NO, TRANSACTION_KEY, REQUESTED_AT);
    }

    private Order createOrder() {
        OrderItem item = OrderItem.create(1L, "애플", 1L, "아이폰 16 Pro", AMOUNT, 1);
        return Order.create(USER_ID, OrderItems.of(List.of(item)));
    }
}
