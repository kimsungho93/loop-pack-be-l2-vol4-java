package com.loopers.catalog.application.listener;

import com.loopers.catalog.application.CatalogEventMessage;
import com.loopers.catalog.application.CatalogEventOutboxWriter;
import com.loopers.catalog.application.CatalogEventType;
import com.loopers.like.application.event.ProductLikedEvent;
import com.loopers.like.application.event.ProductUnlikedEvent;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProductLikeCatalogEventOutboxListenerTest {

    private static final Long USER_ID = 1L;
    private static final Long PRODUCT_ID = 101L;
    private static final ZonedDateTime OCCURRED_AT = ZonedDateTime.parse("2026-07-02T10:00:00+09:00");

    @Mock
    private CatalogEventOutboxWriter catalogEventOutboxWriter;

    @InjectMocks
    private ProductLikeCatalogEventOutboxListener listener;

    @DisplayName("좋아요 catalog 이벤트 outbox 메시지를 기록한다")
    @Nested
    class Record {

        @DisplayName("좋아요 이벤트를 PRODUCT_LIKED 메시지로 기록한다")
        @Test
        void recordsProductLikedMessage_whenProductLikedEventArrives() {
            // arrange
            ProductLikedEvent event = ProductLikedEvent.occurred(USER_ID, PRODUCT_ID, OCCURRED_AT);

            // act
            listener.recordProductLiked(event);

            // assert
            CatalogEventMessage message = captureMessage();
            assertThat(message.eventType()).isEqualTo(CatalogEventType.PRODUCT_LIKED);
            assertThat(message.productId()).isEqualTo(PRODUCT_ID);
            assertThat(message.userId()).isEqualTo(USER_ID);
            assertThat(message.brandId()).isNull();
            assertThat(message.delta()).isEqualTo(1);
            assertThat(message.occurredAt()).isEqualTo(OCCURRED_AT);
        }

        @DisplayName("좋아요 취소 이벤트를 PRODUCT_UNLIKED 메시지로 기록한다")
        @Test
        void recordsProductUnlikedMessage_whenProductUnlikedEventArrives() {
            // arrange
            ProductUnlikedEvent event = ProductUnlikedEvent.occurred(USER_ID, PRODUCT_ID, OCCURRED_AT);

            // act
            listener.recordProductUnliked(event);

            // assert
            CatalogEventMessage message = captureMessage();
            assertThat(message.eventType()).isEqualTo(CatalogEventType.PRODUCT_UNLIKED);
            assertThat(message.productId()).isEqualTo(PRODUCT_ID);
            assertThat(message.userId()).isEqualTo(USER_ID);
            assertThat(message.brandId()).isNull();
            assertThat(message.delta()).isEqualTo(-1);
            assertThat(message.occurredAt()).isEqualTo(OCCURRED_AT);
        }
    }

    @DisplayName("트랜잭션 정책")
    @Nested
    class TransactionPolicy {

        @DisplayName("좋아요 이벤트는 커밋 전에 같은 트랜잭션에서 outbox에 기록한다")
        @Test
        void recordsLikeEventsBeforeCommit_withoutFallbackExecution() throws NoSuchMethodException {
            assertTransactionalEventListener("recordProductLiked", ProductLikedEvent.class);
            assertTransactionalEventListener("recordProductUnliked", ProductUnlikedEvent.class);
        }
    }

    private CatalogEventMessage captureMessage() {
        ArgumentCaptor<CatalogEventMessage> captor = ArgumentCaptor.forClass(CatalogEventMessage.class);
        verify(catalogEventOutboxWriter).save(captor.capture());
        return captor.getValue();
    }

    private void assertTransactionalEventListener(String methodName, Class<?> eventType) throws NoSuchMethodException {
        Method method = ProductLikeCatalogEventOutboxListener.class.getMethod(methodName, eventType);
        TransactionalEventListener annotation = method.getAnnotation(TransactionalEventListener.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.BEFORE_COMMIT);
        assertThat(annotation.fallbackExecution()).isFalse();
    }
}
