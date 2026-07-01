package com.loopers.product.application;

import com.loopers.brand.application.BrandInfo;
import com.loopers.product.application.event.ProductEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductFacadeTest {

    private static final Long USER_ID = 1L;
    private static final Long PRODUCT_ID = 101L;
    private static final ZonedDateTime OCCURRED_AT = ZonedDateTime.parse("2026-06-25T10:00:05+09:00");

    @Mock
    private ProductListQuery productListQuery;

    @Mock
    private ProductDetailQuery productDetailQuery;

    @Mock
    private ProductEventPublisher productEventPublisher;

    @InjectMocks
    private ProductFacade productFacade;

    @DisplayName("상품 상세를 조회할 때")
    @Nested
    class GetProduct {

        @DisplayName("상품 조회 성공 후 상품 조회 이벤트를 발행한다.")
        @Test
        void publishesProductViewedEvent_afterProductDetailIsFound() {
            // arrange
            ProductDetailInfo product = createProductDetailInfo();
            when(productDetailQuery.findVisibleProduct(PRODUCT_ID)).thenReturn(Optional.of(product));

            // act
            ProductDetailInfo result = productFacade.getProduct(PRODUCT_ID, USER_ID);

            // assert
            assertThat(result).isEqualTo(product);
            verify(productEventPublisher).publishViewed(eq(USER_ID), eq(product), any(ZonedDateTime.class));
        }
    }

    private ProductDetailInfo createProductDetailInfo() {
        BrandInfo brand = new BrandInfo(1L, "Apple", "Premium device brand", OCCURRED_AT, OCCURRED_AT, null);
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
