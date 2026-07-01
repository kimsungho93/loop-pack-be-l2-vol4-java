package com.loopers.catalog.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.catalog.application.CatalogEventMessage;
import com.loopers.catalog.application.CatalogEventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CatalogEventOutboxWriterImplTest {

    private static final Long USER_ID = 1L;
    private static final Long PRODUCT_ID = 101L;
    private static final ZonedDateTime OCCURRED_AT = ZonedDateTime.parse("2026-07-02T10:00:00+09:00");

    @Mock
    private CatalogEventOutboxJpaRepository catalogEventOutboxJpaRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private CatalogEventOutboxWriterImpl writer;

    @DisplayName("catalog outbox를 저장한다")
    @Nested
    class Save {

        @DisplayName("UUID v4 eventId와 PENDING 상태로 outbox를 저장한다")
        @Test
        void savesPendingOutboxWithUuidV4EventId() {
            // arrange
            CatalogEventMessage message = CatalogEventMessage.productLiked(USER_ID, PRODUCT_ID, OCCURRED_AT);

            // act
            writer.save(message);

            // assert
            CatalogEventOutbox outbox = captureOutbox();
            UUID eventId = UUID.fromString(outbox.getEventId());

            assertThat(eventId.version()).isEqualTo(4);
            assertThat(outbox.getEventType()).isEqualTo(CatalogEventType.PRODUCT_LIKED);
            assertThat(outbox.getAggregateType()).isEqualTo("PRODUCT");
            assertThat(outbox.getAggregateId()).isEqualTo(PRODUCT_ID);
            assertThat(outbox.getPartitionKey()).isEqualTo(String.valueOf(PRODUCT_ID));
            assertThat(outbox.getStatus()).isEqualTo(CatalogEventOutboxStatus.PENDING);
            assertThat(outbox.getRetryCount()).isZero();
            assertThat(outbox.getOccurredAt()).isEqualTo(OCCURRED_AT);
            assertThat(outbox.getPayload()).contains("\"productId\":101");
            assertThat(outbox.getPayload()).contains("\"userId\":1");
            assertThat(outbox.getPayload()).contains("\"delta\":1");
        }
    }

    private CatalogEventOutbox captureOutbox() {
        ArgumentCaptor<CatalogEventOutbox> captor = ArgumentCaptor.forClass(CatalogEventOutbox.class);
        verify(catalogEventOutboxJpaRepository).save(captor.capture());
        return captor.getValue();
    }
}
