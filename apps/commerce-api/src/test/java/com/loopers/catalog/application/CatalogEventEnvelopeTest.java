package com.loopers.catalog.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogEventEnvelopeTest {

    private static final ZonedDateTime OCCURRED_AT = ZonedDateTime.parse("2026-07-02T10:00:00+09:00");

    @DisplayName("catalog 이벤트 발행 메시지를 검증한다")
    @Nested
    class Validate {

        @DisplayName("eventId가 비어 있으면 예외가 발생한다")
        @Test
        void throwsException_whenEventIdIsBlank() {
            // act & assert
            assertThatThrownBy(() -> new CatalogEventEnvelope(
                " ",
                CatalogEventType.PRODUCT_LIKED,
                "PRODUCT",
                101L,
                new CatalogEventPayload(101L, 1L, null, 1),
                OCCURRED_AT
            )).isInstanceOf(IllegalArgumentException.class);
        }

        @DisplayName("payload가 없으면 예외가 발생한다")
        @Test
        void throwsException_whenPayloadIsNull() {
            // act & assert
            assertThatThrownBy(() -> new CatalogEventEnvelope(
                "event-1",
                CatalogEventType.PRODUCT_LIKED,
                "PRODUCT",
                101L,
                null,
                OCCURRED_AT
            )).isInstanceOf(NullPointerException.class);
        }
    }
}
