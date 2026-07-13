package com.loopers.metrics.application;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductMetricEventHandlerTest {

    private static final ZonedDateTime OCCURRED_AT = ZonedDateTime.parse("2026-07-02T10:00:00+09:00");
    private static final EventHandlingMetadata METADATA = new EventHandlingMetadata("catalog-events", 0, 10L);

    @Mock
    private EventHandledRepository eventHandledRepository;

    @Mock
    private ProductMetricsRepository productMetricsRepository;

    @Mock
    private ProductMetricHourlyRepository productMetricHourlyRepository;

    @InjectMocks
    private ProductMetricEventHandler handler;

    @DisplayName("catalog 이벤트로 상품 지표를 집계할 때")
    @Nested
    class Handle {

        @DisplayName("이미 처리한 이벤트면 상품 지표를 다시 변경하지 않는다.")
        @Test
        void skipsMetrics_whenEventAlreadyHandled() {
            // arrange
            CatalogEventEnvelope event = likedEvent("event-1");
            when(eventHandledRepository.saveIfAbsent(any(), any(), any())).thenReturn(false);

            // act
            handler.handle(event, METADATA);

            // assert
            verify(productMetricsRepository, never()).add(any(), any());
            verify(productMetricHourlyRepository, never()).add(any(), any());
        }

        @DisplayName("상품 조회 이벤트면 조회수를 1 증가시킨다.")
        @Test
        void increasesViewCount_whenProductViewed() {
            // arrange
            CatalogEventEnvelope event = viewedEvent("event-1");
            when(eventHandledRepository.saveIfAbsent(any(), any(), any())).thenReturn(true);

            // act
            handler.handle(event, METADATA);

            // assert
            ProductMetricDelta delta = captureDelta();
            assertThat(delta).isEqualTo(new ProductMetricDelta(101L, 0L, 1L, 0L));
        }

        @DisplayName("신규 상품 이벤트면 발생 시간 Window의 Raw Metric을 저장한다.")
        @Test
        void addsHourlyRawMetric_whenEventIsNew() {
            // arrange
            CatalogEventEnvelope event = viewedEvent("event-1");
            when(eventHandledRepository.saveIfAbsent(any(), any(), any())).thenReturn(true);

            // act
            handler.handle(event, METADATA);

            // assert
            ArgumentCaptor<ProductMetricHourlyDelta> captor = ArgumentCaptor.forClass(ProductMetricHourlyDelta.class);
            verify(productMetricHourlyRepository).add(captor.capture(), any());
            assertThat(captor.getValue()).isEqualTo(new ProductMetricHourlyDelta(
                OCCURRED_AT.toLocalDateTime().withMinute(0).withSecond(0).withNano(0),
                101L,
                1,
                0,
                0,
                0
            ));
        }

        @DisplayName("좋아요 이벤트면 좋아요 수를 1 증가시킨다.")
        @Test
        void increasesLikeCount_whenProductLiked() {
            // arrange
            CatalogEventEnvelope event = likedEvent("event-1");
            when(eventHandledRepository.saveIfAbsent(any(), any(), any())).thenReturn(true);

            // act
            handler.handle(event, METADATA);

            // assert
            ProductMetricDelta delta = captureDelta();
            assertThat(delta).isEqualTo(new ProductMetricDelta(101L, 1L, 0L, 0L));
        }

        @DisplayName("좋아요 취소 이벤트면 좋아요 수를 1 감소시킨다.")
        @Test
        void decreasesLikeCount_whenProductUnliked() {
            // arrange
            CatalogEventEnvelope event = unlikedEvent("event-1");
            when(eventHandledRepository.saveIfAbsent(any(), any(), any())).thenReturn(true);

            // act
            handler.handle(event, METADATA);

            // assert
            ProductMetricDelta delta = captureDelta();
            assertThat(delta).isEqualTo(new ProductMetricDelta(101L, -1L, 0L, 0L));
        }

        @DisplayName("상품 주문 이벤트면 판매 수량을 주문 수량만큼 증가시킨다.")
        @Test
        void increasesSalesCountByOrderQuantity_whenProductOrdered() {
            // arrange
            CatalogEventEnvelope event = orderedEvent("event-1");
            when(eventHandledRepository.saveIfAbsent(any(), any(), any())).thenReturn(true);

            // act
            handler.handle(event, METADATA);

            // assert
            ProductMetricDelta delta = captureDelta();
            assertThat(delta).isEqualTo(new ProductMetricDelta(101L, 0L, 0L, 2L));
        }
    }

    private ProductMetricDelta captureDelta() {
        ArgumentCaptor<ProductMetricDelta> captor = ArgumentCaptor.forClass(ProductMetricDelta.class);
        verify(productMetricsRepository).add(captor.capture(), any());
        return captor.getValue();
    }

    private CatalogEventEnvelope viewedEvent(String eventId) {
        return new CatalogEventEnvelope(
            eventId,
            CatalogEventType.PRODUCT_VIEWED,
            "PRODUCT",
            101L,
            new CatalogEventPayload(101L, 1L, 10L, null),
            OCCURRED_AT
        );
    }

    private CatalogEventEnvelope likedEvent(String eventId) {
        return new CatalogEventEnvelope(
            eventId,
            CatalogEventType.PRODUCT_LIKED,
            "PRODUCT",
            101L,
            new CatalogEventPayload(101L, 1L, null, 1),
            OCCURRED_AT
        );
    }

    private CatalogEventEnvelope unlikedEvent(String eventId) {
        return new CatalogEventEnvelope(
            eventId,
            CatalogEventType.PRODUCT_UNLIKED,
            "PRODUCT",
            101L,
            new CatalogEventPayload(101L, 1L, null, -1),
            OCCURRED_AT
        );
    }

    private CatalogEventEnvelope orderedEvent(String eventId) {
        return new CatalogEventEnvelope(
            eventId,
            CatalogEventType.PRODUCT_ORDERED,
            "PRODUCT",
            101L,
            new CatalogEventPayload(
                101L,
                1L,
                null,
                null,
                500L,
                2,
                12_500L,
                25_000L
            ),
            OCCURRED_AT
        );
    }
}
