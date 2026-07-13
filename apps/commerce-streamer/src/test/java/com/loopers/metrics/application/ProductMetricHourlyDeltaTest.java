package com.loopers.metrics.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductMetricHourlyDeltaTest {

    private static final ZonedDateTime OCCURRED_AT = ZonedDateTime.parse("2026-07-12T15:30:00Z");

    @DisplayName("상품 조회 이벤트를 서울 시간 Window의 조회수 Delta로 변환한다.")
    @Test
    void convertsProductViewedEvent() {
        // arrange
        CatalogEventEnvelope event = event(CatalogEventType.PRODUCT_VIEWED, null);

        // act
        ProductMetricHourlyDelta delta = ProductMetricHourlyDelta.from(event);

        // assert
        assertThat(delta).isEqualTo(new ProductMetricHourlyDelta(
            LocalDateTime.of(2026, 7, 13, 0, 0),
            101L,
            1,
            0,
            0,
            0
        ));
    }

    @DisplayName("상품 좋아요 이벤트를 좋아요 증가 Delta로 변환한다.")
    @Test
    void convertsProductLikedEvent() {
        // arrange
        CatalogEventEnvelope event = event(CatalogEventType.PRODUCT_LIKED, 1);

        // act
        ProductMetricHourlyDelta delta = ProductMetricHourlyDelta.from(event);

        // assert
        assertThat(delta.likeDelta()).isEqualTo(1);
    }

    @DisplayName("상품 좋아요 취소 이벤트를 좋아요 감소 Delta로 변환한다.")
    @Test
    void convertsProductUnlikedEvent() {
        // arrange
        CatalogEventEnvelope event = event(CatalogEventType.PRODUCT_UNLIKED, -1);

        // act
        ProductMetricHourlyDelta delta = ProductMetricHourlyDelta.from(event);

        // assert
        assertThat(delta.likeDelta()).isEqualTo(-1);
    }

    @DisplayName("상품 주문 이벤트를 주문 수량과 주문 금액 Delta로 변환한다.")
    @Test
    void convertsProductOrderedEvent() {
        // arrange
        CatalogEventEnvelope event = orderedEvent(2, 12_500, 25_000);

        // act
        ProductMetricHourlyDelta delta = ProductMetricHourlyDelta.from(event);

        // assert
        assertThat(delta).isEqualTo(new ProductMetricHourlyDelta(
            LocalDateTime.of(2026, 7, 13, 0, 0),
            101L,
            0,
            0,
            2,
            25_000
        ));
    }

    @DisplayName("상품 주문의 단가와 수량을 곱한 금액이 총액과 다르면 변환을 거부한다.")
    @Test
    void rejectsProductOrderedEvent_whenTotalPriceDoesNotMatch() {
        // arrange
        CatalogEventEnvelope event = orderedEvent(2, 12_500, 20_000);

        // act & assert
        assertThatThrownBy(() -> ProductMetricHourlyDelta.from(event))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("totalPrice must equal unitPrice multiplied by quantity");
    }

    private CatalogEventEnvelope event(CatalogEventType eventType, Integer delta) {
        return new CatalogEventEnvelope(
            "event-1",
            eventType,
            "PRODUCT",
            101L,
            new CatalogEventPayload(101L, 1L, null, delta),
            OCCURRED_AT
        );
    }

    private CatalogEventEnvelope orderedEvent(int quantity, long unitPrice, long totalPrice) {
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
                500L,
                quantity,
                unitPrice,
                totalPrice
            ),
            OCCURRED_AT
        );
    }
}
