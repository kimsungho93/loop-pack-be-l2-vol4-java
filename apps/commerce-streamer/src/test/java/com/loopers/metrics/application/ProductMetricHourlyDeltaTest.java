package com.loopers.metrics.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

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
}
