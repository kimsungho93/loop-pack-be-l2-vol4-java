package com.loopers.catalog.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogEventOutboxRelayTest {

    private static final ZonedDateTime OCCURRED_AT = ZonedDateTime.parse("2026-07-02T10:00:00+09:00");

    @Mock
    private CatalogEventOutboxRelayRepository catalogEventOutboxRelayRepository;

    @Mock
    private CatalogEventPublisher catalogEventPublisher;

    @Mock
    private CatalogEventOutboxRelayProperties properties;

    @InjectMocks
    private CatalogEventOutboxRelay relay;

    @DisplayName("catalog outbox를 Kafka로 발행한다")
    @Nested
    class Relay {

        @DisplayName("발행에 성공하면 PUBLISHED 상태로 마킹한다")
        @Test
        void marksPublished_whenPublishSucceeds() {
            // arrange
            CatalogEventOutboxRelayItem item = createItem(1L, "event-1");
            when(properties.chunkSize()).thenReturn(100);
            when(catalogEventOutboxRelayRepository.findPending(any(ZonedDateTime.class), eq(100)))
                .thenReturn(List.of(item));

            // act
            int relayedCount = relay.relay();

            // assert
            assertThat(relayedCount).isEqualTo(1);
            verify(catalogEventPublisher).publish(item);
            verify(catalogEventOutboxRelayRepository).markPublished(eq(1L), any(ZonedDateTime.class));
        }

        @DisplayName("발행에 실패하면 재시도 대상으로 마킹하고 다음 outbox 처리를 이어간다")
        @Test
        void marksPublishFailedAndContinues_whenPublishFails() {
            // arrange
            CatalogEventOutboxRelayItem failedItem = createItem(1L, "event-1");
            CatalogEventOutboxRelayItem nextItem = createItem(2L, "event-2");
            RuntimeException publishException = new RuntimeException("kafka timeout");

            when(properties.chunkSize()).thenReturn(100);
            when(properties.maxRetryCount()).thenReturn(3);
            when(catalogEventOutboxRelayRepository.findPending(any(ZonedDateTime.class), eq(100)))
                .thenReturn(List.of(failedItem, nextItem));
            doThrow(publishException).when(catalogEventPublisher).publish(failedItem);

            // act
            int relayedCount = relay.relay();

            // assert
            assertThat(relayedCount).isEqualTo(2);
            verify(catalogEventPublisher).publish(failedItem);
            verify(catalogEventPublisher).publish(nextItem);
            verify(catalogEventOutboxRelayRepository).markPublishFailed(
                eq(1L),
                eq("kafka timeout"),
                eq(3),
                any(ZonedDateTime.class)
            );
            verify(catalogEventOutboxRelayRepository).markPublished(eq(2L), any(ZonedDateTime.class));
        }

        @DisplayName("발행 성공 후 상태 마킹에 실패하면 발행 실패로 기록하지 않는다")
        @Test
        void doesNotMarkPublishFailed_whenMarkPublishedFailsAfterPublishSucceeds() {
            // arrange
            CatalogEventOutboxRelayItem item = createItem(1L, "event-1");
            RuntimeException markException = new RuntimeException("db unavailable");

            when(properties.chunkSize()).thenReturn(100);
            when(catalogEventOutboxRelayRepository.findPending(any(ZonedDateTime.class), eq(100)))
                .thenReturn(List.of(item));
            doThrow(markException)
                .when(catalogEventOutboxRelayRepository)
                .markPublished(eq(1L), any(ZonedDateTime.class));

            // act & assert
            assertThatThrownBy(() -> relay.relay())
                .isInstanceOf(RuntimeException.class)
                .hasMessage("db unavailable");

            verify(catalogEventPublisher).publish(item);
            verify(catalogEventOutboxRelayRepository, never()).markPublishFailed(
                eq(1L),
                any(),
                anyInt(),
                any(ZonedDateTime.class)
            );
        }

        @DisplayName("발행할 outbox가 없으면 상태 변경을 하지 않는다")
        @Test
        void doesNothing_whenPendingOutboxDoesNotExist() {
            // arrange
            when(properties.chunkSize()).thenReturn(100);
            when(catalogEventOutboxRelayRepository.findPending(any(ZonedDateTime.class), eq(100)))
                .thenReturn(List.of());

            // act
            int relayedCount = relay.relay();

            // assert
            assertThat(relayedCount).isZero();
            verifyNoMoreInteractions(catalogEventPublisher);
        }
    }

    private CatalogEventOutboxRelayItem createItem(Long outboxId, String eventId) {
        return new CatalogEventOutboxRelayItem(
            outboxId,
            eventId,
            CatalogEventType.PRODUCT_LIKED,
            "PRODUCT",
            101L,
            "101",
            "{\"productId\":101,\"userId\":1,\"delta\":1}",
            OCCURRED_AT
        );
    }
}
