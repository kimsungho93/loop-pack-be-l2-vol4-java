package com.loopers.catalog.application.listener;

import com.loopers.catalog.application.CatalogEventMessage;
import com.loopers.catalog.application.CatalogEventOutboxWriter;
import com.loopers.catalog.application.CatalogEventType;
import com.loopers.product.application.event.ProductViewedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProductViewCatalogEventOutboxListenerTest {

    private static final Long USER_ID = 1L;
    private static final Long PRODUCT_ID = 101L;
    private static final Long BRAND_ID = 10L;
    private static final ZonedDateTime OCCURRED_AT = ZonedDateTime.parse("2026-07-02T10:00:00+09:00");

    @Mock
    private CatalogEventOutboxWriter catalogEventOutboxWriter;

    @InjectMocks
    private ProductViewCatalogEventOutboxListener listener;

    @DisplayName("상품 조회 catalog 이벤트 outbox 메시지를 기록한다")
    @Nested
    class Record {

        @DisplayName("상품 조회 이벤트를 PRODUCT_VIEWED 메시지로 기록한다")
        @Test
        void recordsProductViewedMessage_whenProductViewedEventArrives() {
            // arrange
            ProductViewedEvent event = new ProductViewedEvent(USER_ID, PRODUCT_ID, BRAND_ID, OCCURRED_AT);

            // act
            listener.recordProductViewed(event);

            // assert
            CatalogEventMessage message = captureMessage();
            assertThat(message.eventType()).isEqualTo(CatalogEventType.PRODUCT_VIEWED);
            assertThat(message.productId()).isEqualTo(PRODUCT_ID);
            assertThat(message.userId()).isEqualTo(USER_ID);
            assertThat(message.brandId()).isEqualTo(BRAND_ID);
            assertThat(message.delta()).isNull();
            assertThat(message.occurredAt()).isEqualTo(OCCURRED_AT);
        }

        @DisplayName("상품 조회 이벤트 기록에 실패해도 조회 흐름으로 예외를 전파하지 않는다")
        @Test
        void doesNotThrow_whenProductViewedMessageRecordFails() {
            // arrange
            ProductViewedEvent event = new ProductViewedEvent(USER_ID, PRODUCT_ID, BRAND_ID, OCCURRED_AT);
            CatalogEventMessage message = CatalogEventMessage.productViewed(USER_ID, PRODUCT_ID, BRAND_ID, OCCURRED_AT);
            doThrow(new RuntimeException("save failed")).when(catalogEventOutboxWriter).save(message);

            // act & assert
            assertThatNoException().isThrownBy(() -> listener.recordProductViewed(event));
        }
    }

    private CatalogEventMessage captureMessage() {
        ArgumentCaptor<CatalogEventMessage> captor = ArgumentCaptor.forClass(CatalogEventMessage.class);
        verify(catalogEventOutboxWriter).save(captor.capture());
        return captor.getValue();
    }
}
