package com.loopers.like.application.listener;

import com.loopers.like.application.event.ProductLikedEvent;
import com.loopers.like.application.event.ProductUnlikedEvent;
import com.loopers.like.application.ProductLikeCountChangeRecorder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProductLikeCountChangeListenerTest {

    private static final Long USER_ID = 1L;
    private static final Long PRODUCT_ID = 101L;
    private static final ZonedDateTime OCCURRED_AT = ZonedDateTime.parse("2026-06-25T10:00:05+09:00");

    @Mock
    private ProductLikeCountChangeRecorder productLikeCountChangeRecorder;

    @InjectMocks
    private ProductLikeCountChangeListener listener;

    @DisplayName("상품 좋아요 수 변경을 기록할 때")
    @Nested
    class Record {

        @DisplayName("좋아요 생성 이벤트를 증가 변경 기록으로 저장한다.")
        @Test
        void recordsIncreaseChange_whenProductLikedEventArrives() {
            // arrange
            ProductLikedEvent event = ProductLikedEvent.occurred(USER_ID, PRODUCT_ID, OCCURRED_AT);

            // act
            listener.recordIncrease(event);

            // assert
            verify(productLikeCountChangeRecorder).increase(PRODUCT_ID);
        }

        @DisplayName("좋아요 취소 이벤트를 감소 변경 기록으로 저장한다.")
        @Test
        void recordsDecreaseChange_whenProductUnlikedEventArrives() {
            // arrange
            ProductUnlikedEvent event = ProductUnlikedEvent.occurred(USER_ID, PRODUCT_ID, OCCURRED_AT);

            // act
            listener.recordDecrease(event);

            // assert
            verify(productLikeCountChangeRecorder).decrease(PRODUCT_ID);
        }

        @DisplayName("변경 기록 저장에 실패해도 리스너 밖으로 예외를 전파하지 않는다.")
        @Test
        void doesNotThrow_whenCountChangeRecordFails() {
            // arrange
            ProductLikedEvent event = ProductLikedEvent.occurred(USER_ID, PRODUCT_ID, OCCURRED_AT);
            doThrow(new RuntimeException("save failed")).when(productLikeCountChangeRecorder).increase(PRODUCT_ID);

            // act
            listener.recordIncrease(event);

            // assert
            verify(productLikeCountChangeRecorder).increase(PRODUCT_ID);
        }
    }
}
