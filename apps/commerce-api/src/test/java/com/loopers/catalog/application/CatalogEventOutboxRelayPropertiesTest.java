package com.loopers.catalog.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogEventOutboxRelayPropertiesTest {

    @DisplayName("catalog outbox relay 설정을 검증한다")
    @Nested
    class Validate {

        @DisplayName("모든 설정값이 양수이면 생성된다")
        @Test
        void createsProperties_whenAllValuesArePositive() {
            // act
            CatalogEventOutboxRelayProperties properties = new CatalogEventOutboxRelayProperties(
                1_000L,
                100,
                3,
                Duration.ofSeconds(3)
            );

            // assert
            assertThat(properties.relayDelayMs()).isEqualTo(1_000L);
            assertThat(properties.chunkSize()).isEqualTo(100);
            assertThat(properties.maxRetryCount()).isEqualTo(3);
            assertThat(properties.sendTimeout()).isEqualTo(Duration.ofSeconds(3));
        }

        @DisplayName("relay 주기가 양수가 아니면 예외가 발생한다")
        @Test
        void throwsException_whenRelayDelayIsNotPositive() {
            // act & assert
            assertThatThrownBy(() -> new CatalogEventOutboxRelayProperties(
                0L,
                100,
                3,
                Duration.ofSeconds(3)
            )).isInstanceOf(IllegalArgumentException.class);
        }

        @DisplayName("chunk size가 양수가 아니면 예외가 발생한다")
        @Test
        void throwsException_whenChunkSizeIsNotPositive() {
            // act & assert
            assertThatThrownBy(() -> new CatalogEventOutboxRelayProperties(
                1_000L,
                0,
                3,
                Duration.ofSeconds(3)
            )).isInstanceOf(IllegalArgumentException.class);
        }

        @DisplayName("최대 재시도 횟수가 양수가 아니면 예외가 발생한다")
        @Test
        void throwsException_whenMaxRetryCountIsNotPositive() {
            // act & assert
            assertThatThrownBy(() -> new CatalogEventOutboxRelayProperties(
                1_000L,
                100,
                0,
                Duration.ofSeconds(3)
            )).isInstanceOf(IllegalArgumentException.class);
        }

        @DisplayName("send timeout이 양수가 아니면 예외가 발생한다")
        @Test
        void throwsException_whenSendTimeoutIsNotPositive() {
            // act & assert
            assertThatThrownBy(() -> new CatalogEventOutboxRelayProperties(
                1_000L,
                100,
                3,
                Duration.ZERO
            )).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
