package com.loopers.ranking.application;

import com.loopers.metrics.application.CatalogEventEnvelope;
import com.loopers.metrics.application.CatalogEventPayload;
import com.loopers.metrics.application.CatalogEventType;
import com.loopers.ranking.RankingScorePolicy;
import com.loopers.ranking.RankingWindow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RankingScoreEventHandlerTest {

    private final RankingScorePolicy scorePolicy = new RankingScorePolicy(0.1, 0.2, 0.7, 10_000);
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-12T15:00:00Z"), ZoneOffset.UTC);

    private FakeRankingScoreRepository repository;
    private RankingScoreEventHandler handler;

    @BeforeEach
    void setUp() {
        repository = new FakeRankingScoreRepository();
        handler = new RankingScoreEventHandler(repository, scorePolicy, clock);
    }

    @DisplayName("Catalog 이벤트의 Ranking 점수를 반영할 때")
    @Nested
    class Handle {

        @DisplayName("상품 조회 이벤트를 발생 시간 Window의 조회 점수로 반영한다.")
        @Test
        void appliesViewScoreToOccurredAtWindow() {
            // arrange
            CatalogEventEnvelope event = event(
                "event-1",
                CatalogEventType.PRODUCT_VIEWED,
                101L,
                ZonedDateTime.parse("2026-07-12T15:30:00Z"),
                null
            );

            // act
            handler.handle(List.of(event));

            // assert
            assertThat(repository.batches()).containsExactly(new RankingScoreBatch(
                new RankingWindow(LocalDate.of(2026, 7, 13), 0),
                101L,
                List.of(new RankingScoreDelta("event-1", 0.1))
            ));
        }

        @DisplayName("상품 좋아요 이벤트를 좋아요 증가 점수로 반영한다.")
        @Test
        void appliesLikeScore() {
            // arrange
            CatalogEventEnvelope event = event(
                "event-1",
                CatalogEventType.PRODUCT_LIKED,
                101L,
                ZonedDateTime.parse("2026-07-13T10:30:00+09:00"),
                1
            );

            // act
            handler.handle(List.of(event));

            // assert
            assertThat(repository.batches().getFirst().deltas())
                .containsExactly(new RankingScoreDelta("event-1", 0.2));
        }

        @DisplayName("상품 좋아요 취소 이벤트를 좋아요 감소 점수로 반영한다.")
        @Test
        void appliesUnlikeScore() {
            // arrange
            CatalogEventEnvelope event = event(
                "event-1",
                CatalogEventType.PRODUCT_UNLIKED,
                101L,
                ZonedDateTime.parse("2026-07-13T10:30:00+09:00"),
                -1
            );

            // act
            handler.handle(List.of(event));

            // assert
            assertThat(repository.batches().getFirst().deltas())
                .containsExactly(new RankingScoreDelta("event-1", -0.2));
        }

        @DisplayName("상품 주문 이벤트를 주문 금액 가중치 점수로 반영한다.")
        @Test
        void appliesOrderAmountScore() {
            // arrange
            CatalogEventEnvelope event = orderedEvent("event-1", 2, 12_500, 25_000);

            // act
            handler.handle(List.of(event));

            // assert
            assertThat(repository.batches().getFirst().deltas())
                .containsExactly(new RankingScoreDelta("event-1", 1.75));
        }

        @DisplayName("같은 상품과 시간 Window의 이벤트를 하나의 Batch로 묶는다.")
        @Test
        void groupsEventsByProductAndWindow() {
            // arrange
            ZonedDateTime occurredAt = ZonedDateTime.parse("2026-07-13T10:30:00+09:00");
            CatalogEventEnvelope viewed = event(
                "event-1",
                CatalogEventType.PRODUCT_VIEWED,
                101L,
                occurredAt,
                null
            );
            CatalogEventEnvelope liked = event(
                "event-2",
                CatalogEventType.PRODUCT_LIKED,
                101L,
                occurredAt.plusMinutes(10),
                1
            );

            // act
            handler.handle(List.of(viewed, liked));

            // assert
            assertThat(repository.batches()).containsExactly(new RankingScoreBatch(
                new RankingWindow(LocalDate.of(2026, 7, 13), 10),
                101L,
                List.of(
                    new RankingScoreDelta("event-1", 0.1),
                    new RankingScoreDelta("event-2", 0.2)
                )
            ));
        }

        @DisplayName("상품 또는 시간 Window가 다르면 별도 Batch로 분리한다.")
        @Test
        void separatesEventsWithDifferentProductOrWindow() {
            // arrange
            CatalogEventEnvelope firstProductAtTen = event(
                "event-1",
                CatalogEventType.PRODUCT_VIEWED,
                101L,
                ZonedDateTime.parse("2026-07-13T10:30:00+09:00"),
                null
            );
            CatalogEventEnvelope secondProductAtTen = event(
                "event-2",
                CatalogEventType.PRODUCT_VIEWED,
                202L,
                ZonedDateTime.parse("2026-07-13T10:40:00+09:00"),
                null
            );
            CatalogEventEnvelope firstProductAtEleven = event(
                "event-3",
                CatalogEventType.PRODUCT_VIEWED,
                101L,
                ZonedDateTime.parse("2026-07-13T11:00:00+09:00"),
                null
            );

            // act
            handler.handle(List.of(firstProductAtTen, secondProductAtTen, firstProductAtEleven));

            // assert
            assertThat(repository.batches()).containsExactly(
                new RankingScoreBatch(
                    new RankingWindow(LocalDate.of(2026, 7, 13), 10),
                    101L,
                    List.of(new RankingScoreDelta("event-1", 0.1))
                ),
                new RankingScoreBatch(
                    new RankingWindow(LocalDate.of(2026, 7, 13), 10),
                    202L,
                    List.of(new RankingScoreDelta("event-2", 0.1))
                ),
                new RankingScoreBatch(
                    new RankingWindow(LocalDate.of(2026, 7, 13), 11),
                    101L,
                    List.of(new RankingScoreDelta("event-3", 0.1))
                )
            );
        }

        @DisplayName("Ranking Key 만료 시각에 도달한 지연 이벤트는 반영하지 않는다.")
        @Test
        void skipsLateEventWhenRankingWindowExpired() {
            // arrange
            CatalogEventEnvelope expiredEvent = event(
                "event-1",
                CatalogEventType.PRODUCT_VIEWED,
                101L,
                ZonedDateTime.parse("2026-07-11T23:30:00+09:00"),
                null
            );

            // act
            handler.handle(List.of(expiredEvent));

            // assert
            assertThat(repository.batches()).isEmpty();
        }

        @DisplayName("Redis Ranking 반영에 실패하면 예외를 전파한다.")
        @Test
        void propagatesExceptionWhenRankingRepositoryFails() {
            // arrange
            CatalogEventEnvelope event = event(
                "event-1",
                CatalogEventType.PRODUCT_VIEWED,
                101L,
                ZonedDateTime.parse("2026-07-13T10:30:00+09:00"),
                null
            );
            repository.failWith(new IllegalStateException("redis unavailable"));

            // act & assert
            assertThatThrownBy(() -> handler.handle(List.of(event)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("redis unavailable");
        }
    }

    private CatalogEventEnvelope event(
        String eventId,
        CatalogEventType eventType,
        long productId,
        ZonedDateTime occurredAt,
        Integer delta
    ) {
        return new CatalogEventEnvelope(
            eventId,
            eventType,
            "PRODUCT",
            productId,
            new CatalogEventPayload(productId, 1L, null, delta),
            occurredAt
        );
    }

    private CatalogEventEnvelope orderedEvent(
        String eventId,
        int quantity,
        long unitPrice,
        long totalPrice
    ) {
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
                quantity,
                unitPrice,
                totalPrice
            ),
            ZonedDateTime.parse("2026-07-13T10:30:00+09:00")
        );
    }

    private static final class FakeRankingScoreRepository implements RankingScoreRepository {

        private final List<RankingScoreBatch> batches = new ArrayList<>();
        private RuntimeException failure;

        @Override
        public long apply(RankingScoreBatch batch) {
            if (failure != null) {
                throw failure;
            }
            batches.add(batch);
            return batch.deltas().size();
        }

        List<RankingScoreBatch> batches() {
            return List.copyOf(batches);
        }

        void failWith(RuntimeException failure) {
            this.failure = failure;
        }
    }
}
