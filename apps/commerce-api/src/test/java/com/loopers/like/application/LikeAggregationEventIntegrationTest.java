package com.loopers.like.application;

import com.loopers.like.domain.LikeService;
import com.loopers.like.domain.ProductLikeCountChangeRepository;
import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductService;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@SpringBootTest
class LikeAggregationEventIntegrationTest {

    private final LikeFacade likeFacade;
    private final LikeService likeService;
    private final ProductService productService;
    private final DatabaseCleanUp databaseCleanUp;

    @MockitoBean
    private ProductLikeCountChangeRepository productLikeCountChangeRepository;

    @Autowired
    LikeAggregationEventIntegrationTest(
        LikeFacade likeFacade,
        LikeService likeService,
        ProductService productService,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.likeFacade = likeFacade;
        this.likeService = likeService;
        this.productService = productService;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("좋아요 집계 이벤트를 처리할 때")
    @Nested
    class LikeAggregation {

        @DisplayName("집계 변경 기록에 실패해도 좋아요 생성은 성공한다.")
        @Test
        void succeedsLike_whenAggregationRecordFails() {
            // arrange
            Long userId = 1L;
            Product product = createProduct();
            doThrow(new RuntimeException("save failed")).when(productLikeCountChangeRepository).save(any());

            // act & assert
            assertThatCode(() -> likeFacade.like(userId, product.getId())).doesNotThrowAnyException();
            assertThat(likeService.countProductLikes(product.getId())).isEqualTo(1);
            verify(productLikeCountChangeRepository).save(any());
        }
    }

    private Product createProduct() {
        return productService.createProduct(
            1L,
            "아이폰 16 Pro",
            "강력한 성능과 정확한 카메라 경험을 제공하는 스마트폰",
            1_550_000L
        );
    }
}
