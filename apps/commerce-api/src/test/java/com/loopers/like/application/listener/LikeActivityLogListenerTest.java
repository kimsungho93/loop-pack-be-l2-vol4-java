package com.loopers.like.application.listener;

import com.loopers.like.application.event.ProductLikedEvent;
import com.loopers.like.application.event.ProductUnlikedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class LikeActivityLogListenerTest {

    private static final Long USER_ID = 1L;
    private static final Long PRODUCT_ID = 101L;
    private static final ZonedDateTime OCCURRED_AT = ZonedDateTime.parse("2026-06-25T10:00:05+09:00");

    private final LikeActivityLogListener listener = new LikeActivityLogListener();

    @DisplayName("좋아요 행동 로그를 남길 때")
    @Nested
    class Record {

        @DisplayName("상품 좋아요 이벤트를 서버 로그로 남긴다.")
        @Test
        void recordsProductLikedActivity(CapturedOutput output) {
            // arrange
            ProductLikedEvent event = ProductLikedEvent.occurred(USER_ID, PRODUCT_ID, OCCURRED_AT);

            // act
            listener.recordProductLiked(event);

            // assert
            assertThat(output).contains("Product liked activity recorded");
            assertThat(output).contains("userId=1");
            assertThat(output).contains("productId=101");
        }

        @DisplayName("상품 좋아요 취소 이벤트를 서버 로그로 남긴다.")
        @Test
        void recordsProductUnlikedActivity(CapturedOutput output) {
            // arrange
            ProductUnlikedEvent event = ProductUnlikedEvent.occurred(USER_ID, PRODUCT_ID, OCCURRED_AT);

            // act
            listener.recordProductUnliked(event);

            // assert
            assertThat(output).contains("Product unliked activity recorded");
            assertThat(output).contains("userId=1");
            assertThat(output).contains("productId=101");
        }
    }
}
