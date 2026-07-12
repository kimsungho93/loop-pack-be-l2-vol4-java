package com.loopers.product.application.listener;

import com.loopers.brand.application.BrandInfo;
import com.loopers.product.application.ProductDetailInfo;
import com.loopers.product.application.event.ProductViewedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class ProductActivityLogListenerTest {

    private static final Long USER_ID = 1L;
    private static final Long PRODUCT_ID = 101L;
    private static final Long BRAND_ID = 1L;
    private static final ZonedDateTime OCCURRED_AT = ZonedDateTime.parse("2026-06-25T10:00:05+09:00");

    private final ProductActivityLogListener listener = new ProductActivityLogListener();

    @DisplayName("상품 행동 로그를 남길 때")
    @Nested
    class Record {

        @DisplayName("상품 조회 이벤트를 서버 로그로 남긴다.")
        @Test
        void recordsProductViewedActivity(CapturedOutput output) {
            // arrange
            ProductViewedEvent event = ProductViewedEvent.from(USER_ID, createProductDetailInfo(), OCCURRED_AT);

            // act
            listener.recordProductViewed(event);

            // assert
            assertThat(output).contains("Product viewed activity recorded");
            assertThat(output).contains("userId=1");
            assertThat(output).contains("productId=101");
        }
    }

    private ProductDetailInfo createProductDetailInfo() {
        BrandInfo brand = new BrandInfo(BRAND_ID, "Apple", "Premium device brand", OCCURRED_AT, OCCURRED_AT, null);
        return new ProductDetailInfo(
            PRODUCT_ID,
            brand,
            "iPhone 16 Pro",
            "High performance smartphone",
            1_550_000L,
            7L
        );
    }
}
