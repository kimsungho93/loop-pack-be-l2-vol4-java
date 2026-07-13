package com.loopers.catalog.application.listener;

import com.loopers.catalog.application.CatalogEventMessage;
import com.loopers.catalog.application.CatalogEventOutboxWriter;
import com.loopers.catalog.application.CatalogEventType;
import com.loopers.payment.application.event.OrderPaidEvent;
import com.loopers.payment.application.event.OrderPaidItemSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderPaidCatalogEventOutboxListenerTest {

    private static final Long USER_ID = 1L;
    private static final Long ORDER_ID = 500L;
    private static final ZonedDateTime OCCURRED_AT = ZonedDateTime.parse("2026-07-13T10:30:00+09:00");

    @Mock
    private CatalogEventOutboxWriter catalogEventOutboxWriter;

    @InjectMocks
    private OrderPaidCatalogEventOutboxListener listener;

    @DisplayName("결제 완료 catalog 이벤트 outbox 메시지를 기록한다")
    @Nested
    class Record {

        @DisplayName("주문 상품마다 PRODUCT_ORDERED 메시지를 하나씩 기록한다")
        @Test
        void recordsProductOrderedMessageForEachItem_whenOrderPaidEventArrives() {
            // arrange
            OrderPaidEvent event = new OrderPaidEvent(
                "payment:10:paid",
                ORDER_ID,
                10L,
                USER_ID,
                45_000L,
                "transaction-key",
                List.of(
                    new OrderPaidItemSnapshot(101L, 10_000L, 2, 20_000L),
                    new OrderPaidItemSnapshot(205L, 25_000L, 1, 25_000L)
                ),
                OCCURRED_AT
            );

            // act
            listener.recordProductOrdered(event);

            // assert
            assertThat(captureMessages()).containsExactly(
                CatalogEventMessage.productOrdered(USER_ID, ORDER_ID, 101L, 2, 10_000L, 20_000L, OCCURRED_AT),
                CatalogEventMessage.productOrdered(USER_ID, ORDER_ID, 205L, 1, 25_000L, 25_000L, OCCURRED_AT)
            );
        }
    }

    @DisplayName("트랜잭션 정책")
    @Nested
    class TransactionPolicy {

        @DisplayName("결제 완료 이벤트는 커밋 전에 같은 트랜잭션에서 outbox에 기록한다")
        @Test
        void recordsOrderPaidEventBeforeCommit_withoutFallbackExecution() throws NoSuchMethodException {
            Method method = OrderPaidCatalogEventOutboxListener.class.getMethod(
                "recordProductOrdered",
                OrderPaidEvent.class
            );
            TransactionalEventListener annotation = method.getAnnotation(TransactionalEventListener.class);

            assertThat(annotation).isNotNull();
            assertThat(annotation.phase()).isEqualTo(TransactionPhase.BEFORE_COMMIT);
            assertThat(annotation.fallbackExecution()).isFalse();
        }
    }

    private List<CatalogEventMessage> captureMessages() {
        ArgumentCaptor<CatalogEventMessage> captor = ArgumentCaptor.forClass(CatalogEventMessage.class);
        verify(catalogEventOutboxWriter, times(2)).save(captor.capture());
        return captor.getAllValues();
    }
}
