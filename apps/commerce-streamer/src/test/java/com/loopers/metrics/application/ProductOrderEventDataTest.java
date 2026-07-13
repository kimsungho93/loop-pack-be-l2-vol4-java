package com.loopers.metrics.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductOrderEventDataTest {

    private static final ZonedDateTime OCCURRED_AT = ZonedDateTime.parse("2026-07-13T10:30:00+09:00");

    @DisplayName("상품 주문 이벤트 데이터를 변환할 때")
    @Nested
    class From {

        @DisplayName("주문 ID가 없으면 변환을 거부한다.")
        @Test
        void rejectsEvent_whenOrderIdIsNull() {
            // arrange
            CatalogEventEnvelope event = event(null, 2, 12_500L, 25_000L);

            // act & assert
            assertThatThrownBy(() -> ProductOrderEventData.from(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("orderId must not be null");
        }

        @DisplayName("주문 수량이 1보다 작으면 변환을 거부한다.")
        @Test
        void rejectsEvent_whenQuantityIsLessThanOne() {
            // arrange
            CatalogEventEnvelope event = event(500L, 0, 12_500L, 0L);

            // act & assert
            assertThatThrownBy(() -> ProductOrderEventData.from(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("quantity must be at least 1");
        }

        @DisplayName("상품 단가가 음수면 변환을 거부한다.")
        @Test
        void rejectsEvent_whenUnitPriceIsNegative() {
            // arrange
            CatalogEventEnvelope event = event(500L, 2, -1L, 0L);

            // act & assert
            assertThatThrownBy(() -> ProductOrderEventData.from(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unitPrice must not be negative");
        }

        @DisplayName("주문 총액이 음수면 변환을 거부한다.")
        @Test
        void rejectsEvent_whenTotalPriceIsNegative() {
            // arrange
            CatalogEventEnvelope event = event(500L, 2, 12_500L, -1L);

            // act & assert
            assertThatThrownBy(() -> ProductOrderEventData.from(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("totalPrice must not be negative");
        }
    }

    private CatalogEventEnvelope event(
        Long orderId,
        Integer quantity,
        Long unitPrice,
        Long totalPrice
    ) {
        return new CatalogEventEnvelope(
            "event-1",
            CatalogEventType.PRODUCT_ORDERED,
            "PRODUCT",
            101L,
            new CatalogEventPayload(
                101L,
                1L,
                null,
                null,
                orderId,
                quantity,
                unitPrice,
                totalPrice
            ),
            OCCURRED_AT
        );
    }
}
