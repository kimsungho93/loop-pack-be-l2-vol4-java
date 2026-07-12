package com.loopers.catalog.infrastructure;

import com.loopers.catalog.application.CatalogEventOutboxRelayItem;
import com.loopers.catalog.application.CatalogEventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogEventOutboxRelayRepositoryImplTest {

    private static final String EVENT_ID = "5b7d0c84-f4e1-4df6-8a74-4f3f7954bc7c";
    private static final Long PRODUCT_ID = 101L;
    private static final String PARTITION_KEY = "101";
    private static final String PAYLOAD = "{\"productId\":101}";
    private static final ZonedDateTime OCCURRED_AT = ZonedDateTime.parse("2026-07-02T10:00:00+09:00");
    private static final ZonedDateTime NOW = ZonedDateTime.parse("2026-07-02T10:00:10+09:00");

    @Mock
    private CatalogEventOutboxJpaRepository catalogEventOutboxJpaRepository;

    @InjectMocks
    private CatalogEventOutboxRelayRepositoryImpl repository;

    @DisplayName("relay 대상 outbox를 조회한다")
    @Nested
    class FindPending {

        @DisplayName("PENDING 상태이고 nextRetryAt이 지난 이벤트를 ID 오름차순으로 조회한다")
        @Test
        void findsPendingOutboxesByStatusAndNextRetryAt() {
            // arrange
            CatalogEventOutbox outbox = createOutbox();
            when(catalogEventOutboxJpaRepository.findPendingForRelay(
                CatalogEventOutboxStatus.PENDING,
                NOW,
                PageRequest.of(0, 100)
            )).thenReturn(List.of(outbox));

            // act
            List<CatalogEventOutboxRelayItem> items = repository.findPending(NOW, 100);

            // assert
            assertThat(items).hasSize(1);
            CatalogEventOutboxRelayItem item = items.get(0);
            assertThat(item.eventId()).isEqualTo(EVENT_ID);
            assertThat(item.eventType()).isEqualTo(CatalogEventType.PRODUCT_LIKED);
            assertThat(item.aggregateType()).isEqualTo("PRODUCT");
            assertThat(item.aggregateId()).isEqualTo(PRODUCT_ID);
            assertThat(item.partitionKey()).isEqualTo(PARTITION_KEY);
            assertThat(item.payload()).isEqualTo(PAYLOAD);
            assertThat(item.occurredAt()).isEqualTo(OCCURRED_AT);
        }
    }

    @DisplayName("outbox 발행 결과를 기록한다")
    @Nested
    class Mark {

        @DisplayName("발행 성공 시 PUBLISHED 상태로 마킹한다")
        @Test
        void marksPublished() {
            // arrange
            CatalogEventOutbox outbox = createOutbox();
            when(catalogEventOutboxJpaRepository.findById(1L)).thenReturn(Optional.of(outbox));

            // act
            repository.markPublished(1L, NOW);

            // assert
            assertThat(outbox.getStatus()).isEqualTo(CatalogEventOutboxStatus.PUBLISHED);
            assertThat(outbox.getPublishedAt()).isEqualTo(NOW);
        }

        @DisplayName("발행 실패 시 재시도 상태를 마킹한다")
        @Test
        void marksPublishFailed() {
            // arrange
            CatalogEventOutbox outbox = createOutbox();
            when(catalogEventOutboxJpaRepository.findById(1L)).thenReturn(Optional.of(outbox));

            // act
            repository.markPublishFailed(1L, "timeout", 3, NOW);

            // assert
            assertThat(outbox.getStatus()).isEqualTo(CatalogEventOutboxStatus.PENDING);
            assertThat(outbox.getRetryCount()).isEqualTo(1);
            assertThat(outbox.getNextRetryAt()).isEqualTo(NOW.plusSeconds(2));
            assertThat(outbox.getLastError()).isEqualTo("timeout");
        }
    }

    private CatalogEventOutbox createOutbox() {
        return CatalogEventOutbox.pending(
            EVENT_ID,
            CatalogEventType.PRODUCT_LIKED,
            PRODUCT_ID,
            PARTITION_KEY,
            PAYLOAD,
            OCCURRED_AT
        );
    }
}
