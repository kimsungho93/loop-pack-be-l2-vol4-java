package com.loopers.catalog.infrastructure;

import com.loopers.catalog.application.CatalogEventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogEventOutboxTest {

    private static final String EVENT_ID = "5b7d0c84-f4e1-4df6-8a74-4f3f7954bc7c";
    private static final Long PRODUCT_ID = 101L;
    private static final String PARTITION_KEY = "101";
    private static final String PAYLOAD = "{\"productId\":101}";
    private static final ZonedDateTime OCCURRED_AT = ZonedDateTime.parse("2026-07-02T10:00:00+09:00");
    private static final ZonedDateTime NOW = ZonedDateTime.parse("2026-07-02T10:00:10+09:00");

    @DisplayName("catalog outbox 상태를 변경한다")
    @Nested
    class ChangeStatus {

        @DisplayName("생성 시 PENDING 상태와 nextRetryAt을 기록한다")
        @Test
        void createsPendingOutboxWithNextRetryAt() {
            // act
            CatalogEventOutbox outbox = createOutbox();

            // assert
            assertThat(outbox.getStatus()).isEqualTo(CatalogEventOutboxStatus.PENDING);
            assertThat(outbox.getRetryCount()).isZero();
            assertThat(outbox.getNextRetryAt()).isEqualTo(OCCURRED_AT);
        }

        @DisplayName("발행 성공 시 PUBLISHED 상태로 변경한다")
        @Test
        void marksPublished() {
            // arrange
            CatalogEventOutbox outbox = createOutbox();

            // act
            outbox.markPublished(NOW);

            // assert
            assertThat(outbox.getStatus()).isEqualTo(CatalogEventOutboxStatus.PUBLISHED);
            assertThat(outbox.getPublishedAt()).isEqualTo(NOW);
            assertThat(outbox.getLastError()).isNull();
        }

        @DisplayName("첫 번째 발행 실패 시 2초 뒤 재시도하도록 PENDING 상태를 유지한다")
        @Test
        void keepsPendingWithTwoSecondsBackoff_whenFirstPublishFails() {
            // arrange
            CatalogEventOutbox outbox = createOutbox();

            // act
            outbox.markPublishFailed("timeout", 3, NOW);

            // assert
            assertThat(outbox.getStatus()).isEqualTo(CatalogEventOutboxStatus.PENDING);
            assertThat(outbox.getRetryCount()).isEqualTo(1);
            assertThat(outbox.getNextRetryAt()).isEqualTo(NOW.plusSeconds(2));
            assertThat(outbox.getLastError()).isEqualTo("timeout");
        }

        @DisplayName("두 번째 발행 실패 시 4초 뒤 재시도하도록 PENDING 상태를 유지한다")
        @Test
        void keepsPendingWithFourSecondsBackoff_whenSecondPublishFails() {
            // arrange
            CatalogEventOutbox outbox = createOutbox();

            // act
            outbox.markPublishFailed("first timeout", 3, NOW);
            outbox.markPublishFailed("second timeout", 3, NOW);

            // assert
            assertThat(outbox.getStatus()).isEqualTo(CatalogEventOutboxStatus.PENDING);
            assertThat(outbox.getRetryCount()).isEqualTo(2);
            assertThat(outbox.getNextRetryAt()).isEqualTo(NOW.plusSeconds(4));
            assertThat(outbox.getLastError()).isEqualTo("second timeout");
        }

        @DisplayName("최대 재시도 횟수에 도달하면 RETRY_EXCEEDED 상태로 변경한다")
        @Test
        void marksRetryExceeded_whenRetryCountReachesMaxRetryCount() {
            // arrange
            CatalogEventOutbox outbox = createOutbox();

            // act
            outbox.markPublishFailed("first timeout", 3, NOW);
            outbox.markPublishFailed("second timeout", 3, NOW);
            outbox.markPublishFailed("third timeout", 3, NOW);

            // assert
            assertThat(outbox.getStatus()).isEqualTo(CatalogEventOutboxStatus.RETRY_EXCEEDED);
            assertThat(outbox.getRetryCount()).isEqualTo(3);
            assertThat(outbox.getLastError()).isEqualTo("third timeout");
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
