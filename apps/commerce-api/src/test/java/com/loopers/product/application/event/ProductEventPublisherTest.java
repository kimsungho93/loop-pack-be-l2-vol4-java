package com.loopers.product.application.event;

import com.loopers.brand.application.BrandInfo;
import com.loopers.product.application.ProductDetailInfo;
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
class ProductEventPublisherTest {

    private static final Long USER_ID = 1L;
    private static final Long PRODUCT_ID = 101L;
    private static final Long BRAND_ID = 1L;
    private static final ZonedDateTime OCCURRED_AT = ZonedDateTime.parse("2026-06-25T10:00:05+09:00");

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @DisplayName("상품 이벤트를 발행할 때")
    @Nested
    class Publish {

        @DisplayName("상품 조회 이벤트는 상품 상세 정보 기반으로 발행된다.")
        @Test
        void publishesProductViewedEvent() {
            // arrange
            ProductDetailInfo product = createProductDetailInfo();
            ProductEventPublisher publisher = new ProductEventPublisher(applicationEventPublisher);

            // act
            publisher.publishViewed(USER_ID, product, OCCURRED_AT);

            // assert
            ArgumentCaptor<ProductViewedEvent> eventCaptor = ArgumentCaptor.forClass(ProductViewedEvent.class);
            verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
            ProductViewedEvent event = eventCaptor.getValue();

            assertAll(
                () -> assertThat(event.userId()).isEqualTo(USER_ID),
                () -> assertThat(event.productId()).isEqualTo(PRODUCT_ID),
                () -> assertThat(event.brandId()).isEqualTo(BRAND_ID),
                () -> assertThat(event.occurredAt()).isEqualTo(OCCURRED_AT)
            );
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
