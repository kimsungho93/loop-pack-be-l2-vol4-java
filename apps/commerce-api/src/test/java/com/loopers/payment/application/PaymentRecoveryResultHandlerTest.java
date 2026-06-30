package com.loopers.payment.application;

import com.loopers.order.domain.Order;
import com.loopers.order.domain.OrderItem;
import com.loopers.order.domain.OrderItems;
import com.loopers.order.domain.OrderService;
import com.loopers.order.domain.OrderStatus;
import com.loopers.payment.application.event.OrderPaymentEventPublisher;
import com.loopers.payment.domain.CardType;
import com.loopers.payment.domain.Payment;
import com.loopers.payment.domain.PaymentFailureReason;
import com.loopers.payment.domain.PaymentGatewayTransactionDetail;
import com.loopers.payment.domain.PaymentService;
import com.loopers.payment.domain.PaymentStatus;
import com.loopers.payment.domain.PgPaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentRecoveryResultHandlerTest {

    private static final Long USER_ID = 1L;
    private static final Long ORDER_ID = 1_351_039_135L;
    private static final long AMOUNT = 5_000L;
    private static final String CARD_NO = "1234-5678-9814-1451";
    private static final String TRANSACTION_KEY = "20250816:TR:9577c5";
    private static final ZonedDateTime REQUESTED_AT = ZonedDateTime.parse("2026-06-25T10:00:00+09:00");
    private static final ZonedDateTime COMPLETED_AT = ZonedDateTime.parse("2026-06-25T10:00:05+09:00");
    private static final ZonedDateTime NEXT_RECOVERY_AT = ZonedDateTime.parse("2026-06-25T10:00:30+09:00");

    @Mock
    private PaymentService paymentService;

    @Mock
    private OrderService orderService;

    @Mock
    private OrderPaymentEventPublisher orderPaymentEventPublisher;

    @DisplayName("PG 조회 결과를 반영할 때")
    @Nested
    class ApplyTransaction {

        @DisplayName("성공 거래이면 결제와 주문을 완료한다.")
        @Test
        void completesPaymentAndOrder_whenTransactionSucceeded() {
            // arrange
            Payment payment = createPendingPayment();
            Order order = createOrder();
            PaymentRecoveryResultHandler handler = new PaymentRecoveryResultHandler(
                paymentService,
                orderService,
                orderPaymentEventPublisher
            );
            when(paymentService.getPayment(payment.getId())).thenReturn(payment);
            when(orderService.getOrder(ORDER_ID)).thenReturn(order);

            // act
            handler.applyTransaction(
                payment.getId(),
                createTransaction(PgPaymentStatus.SUCCESS, "success"),
                COMPLETED_AT,
                NEXT_RECOVERY_AT
            );

            // assert
            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID)
            );
            verify(orderPaymentEventPublisher).publishPaid(payment, COMPLETED_AT);
        }

        @DisplayName("결제가 이미 성공이어도 주문이 아직 완료되지 않았다면 결제 완료 이벤트를 발행한다.")
        @Test
        void publishesPaidEvent_whenOrderNewlyBecomesPaidEvenIfPaymentAlreadySucceeded() {
            // arrange
            Payment payment = createPendingPayment();
            payment.markSucceeded(TRANSACTION_KEY, "success", COMPLETED_AT);
            Order order = createOrder();
            PaymentRecoveryResultHandler handler = new PaymentRecoveryResultHandler(
                paymentService,
                orderService,
                orderPaymentEventPublisher
            );
            when(paymentService.getPayment(payment.getId())).thenReturn(payment);
            when(orderService.getOrder(ORDER_ID)).thenReturn(order);

            // act
            handler.applyTransaction(
                payment.getId(),
                createTransaction(PgPaymentStatus.SUCCESS, "success"),
                COMPLETED_AT,
                NEXT_RECOVERY_AT
            );

            // assert
            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID)
            );
            verify(orderPaymentEventPublisher).publishPaid(payment, COMPLETED_AT);
        }

        @DisplayName("이미 주문까지 결제 완료 상태이면 결제 완료 이벤트를 다시 발행하지 않는다.")
        @Test
        void doesNotPublishPaidEvent_whenTransactionAlreadySucceeded() {
            // arrange
            Payment payment = createPendingPayment();
            payment.markSucceeded(TRANSACTION_KEY, "success", COMPLETED_AT);
            Order order = createOrder();
            order.completePayment();
            PaymentRecoveryResultHandler handler = new PaymentRecoveryResultHandler(
                paymentService,
                orderService,
                orderPaymentEventPublisher
            );
            when(paymentService.getPayment(payment.getId())).thenReturn(payment);
            when(orderService.getOrder(ORDER_ID)).thenReturn(order);

            // act
            handler.applyTransaction(
                payment.getId(),
                createTransaction(PgPaymentStatus.SUCCESS, "success"),
                COMPLETED_AT,
                NEXT_RECOVERY_AT
            );

            // assert
            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID)
            );
            verify(orderPaymentEventPublisher, never()).publishPaid(payment, COMPLETED_AT);
        }

        @DisplayName("대기 거래이면 다음 복구 시각만 예약한다.")
        @Test
        void schedulesRecovery_whenTransactionIsPending() {
            // arrange
            Payment payment = createPendingPayment();
            PaymentRecoveryResultHandler handler = new PaymentRecoveryResultHandler(
                paymentService,
                orderService,
                orderPaymentEventPublisher
            );
            when(paymentService.getPayment(payment.getId())).thenReturn(payment);

            // act
            handler.applyTransaction(
                payment.getId(),
                createTransaction(PgPaymentStatus.PENDING, "pending"),
                COMPLETED_AT,
                NEXT_RECOVERY_AT
            );

            // assert
            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING),
                () -> assertThat(payment.getNextRecoveryAt()).isEqualTo(NEXT_RECOVERY_AT),
                () -> assertThat(payment.getLastRecoveryReason()).isEqualTo("pending")
            );
            verify(orderService, never()).getOrder(ORDER_ID);
            verify(orderPaymentEventPublisher, never()).publishPaid(payment, COMPLETED_AT);
            verify(orderPaymentEventPublisher, never()).publishFailed(payment, COMPLETED_AT);
        }

        @DisplayName("실패 거래이면 결제와 주문을 실패 처리한다.")
        @Test
        void failsPaymentAndOrder_whenTransactionFailed() {
            // arrange
            Payment payment = createPendingPayment();
            Order order = createOrder();
            PaymentRecoveryResultHandler handler = new PaymentRecoveryResultHandler(
                paymentService,
                orderService,
                orderPaymentEventPublisher
            );
            when(paymentService.getPayment(payment.getId())).thenReturn(payment);
            when(orderService.getOrder(ORDER_ID)).thenReturn(order);

            // act
            handler.applyTransaction(
                payment.getId(),
                createTransaction(PgPaymentStatus.FAILED, "한도초과입니다."),
                COMPLETED_AT,
                NEXT_RECOVERY_AT
            );

            // assert
            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED),
                () -> assertThat(payment.getFailureReason()).isEqualTo(PaymentFailureReason.LIMIT_EXCEEDED),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED)
            );
            verify(orderPaymentEventPublisher).publishFailed(payment, COMPLETED_AT);
        }
    }

    private Payment createPendingPayment() {
        return Payment.pending(USER_ID, ORDER_ID, AMOUNT, CardType.SAMSUNG, CARD_NO, TRANSACTION_KEY, REQUESTED_AT);
    }

    private PaymentGatewayTransactionDetail createTransaction(PgPaymentStatus status, String reason) {
        return new PaymentGatewayTransactionDetail(
            TRANSACTION_KEY,
            ORDER_ID,
            CardType.SAMSUNG,
            AMOUNT,
            status,
            reason
        );
    }

    private Order createOrder() {
        OrderItem item = OrderItem.create(1L, "애플", 1L, "아이폰 16 Pro", AMOUNT, 1);
        return Order.create(USER_ID, OrderItems.of(List.of(item)));
    }
}
