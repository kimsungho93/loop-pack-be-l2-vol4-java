package com.loopers.like.application.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProductLikeEventPublisherTest {

    private static final Long USER_ID = 1L;
    private static final Long PRODUCT_ID = 101L;
    private static final ZonedDateTime OCCURRED_AT = ZonedDateTime.parse("2026-06-25T10:00:05+09:00");

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @DisplayName("상품 좋아요 이벤트를 발행할 때")
    @Nested
    class Publish {

        @DisplayName("좋아요 생성 이벤트를 발행한다.")
        @Test
        void publishesProductLikedEvent() {
            // arrange
            ProductLikeEventPublisher publisher = new ProductLikeEventPublisher(applicationEventPublisher);

            // act
            publisher.publishLiked(USER_ID, PRODUCT_ID, OCCURRED_AT);

            // assert
            ArgumentCaptor<ProductLikedEvent> eventCaptor = ArgumentCaptor.forClass(ProductLikedEvent.class);
            verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
            ProductLikedEvent event = eventCaptor.getValue();

            assertAll(
                () -> assertThat(event.userId()).isEqualTo(USER_ID),
                () -> assertThat(event.productId()).isEqualTo(PRODUCT_ID),
                () -> assertThat(event.occurredAt()).isEqualTo(OCCURRED_AT)
            );
        }

        @DisplayName("좋아요 취소 이벤트를 발행한다.")
        @Test
        void publishesProductUnlikedEvent() {
            // arrange
            ProductLikeEventPublisher publisher = new ProductLikeEventPublisher(applicationEventPublisher);

            // act
            publisher.publishUnliked(USER_ID, PRODUCT_ID, OCCURRED_AT);

            // assert
            ArgumentCaptor<ProductUnlikedEvent> eventCaptor = ArgumentCaptor.forClass(ProductUnlikedEvent.class);
            verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
            ProductUnlikedEvent event = eventCaptor.getValue();

            assertAll(
                () -> assertThat(event.userId()).isEqualTo(USER_ID),
                () -> assertThat(event.productId()).isEqualTo(PRODUCT_ID),
                () -> assertThat(event.occurredAt()).isEqualTo(OCCURRED_AT)
            );
        }
    }
}
