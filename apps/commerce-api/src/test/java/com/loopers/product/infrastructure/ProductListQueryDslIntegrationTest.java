package com.loopers.product.infrastructure;

import com.loopers.brand.domain.Brand;
import com.loopers.brand.domain.BrandService;
import com.loopers.product.application.ProductLikeSummaryWriter;
import com.loopers.product.application.ProductListInfo;
import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductService;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ProductListQueryDslIntegrationTest {

    private final ProductListQueryDsl productListQueryDsl;
    private final BrandService brandService;
    private final ProductService productService;
    private final ProductLikeSummaryWriter productLikeSummaryWriter;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    ProductListQueryDslIntegrationTest(
        ProductListQueryDsl productListQueryDsl,
        BrandService brandService,
        ProductService productService,
        ProductLikeSummaryWriter productLikeSummaryWriter,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.productListQueryDsl = productListQueryDsl;
        this.brandService = brandService;
        this.productService = productService;
        this.productLikeSummaryWriter = productLikeSummaryWriter;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("노출 가능한 상품을 ID 목록으로 조회할 때")
    @Nested
    class FindVisibleProductsByIds {

        @DisplayName("삭제 상품은 제외하고 상품·브랜드·좋아요 정보를 일괄 조회한다")
        @Test
        void returnsVisibleProductProjectionsAndExcludesDeletedProducts() {
            // arrange
            Brand brand = brandService.createBrand("애플", "기술과 디자인으로 일상을 새롭게 만드는 브랜드");
            Product iphone = createProduct(brand, "아이폰 16", 1_250_000L);
            Product pro = createProduct(brand, "아이폰 16 Pro", 1_550_000L);
            Product proMax = createProduct(brand, "아이폰 16 Pro Max", 1_900_000L);
            productService.deleteProduct(pro.getId());

            // act
            List<ProductListInfo> result = productListQueryDsl.findVisibleProductsByIds(List.of(
                proMax.getId(),
                pro.getId(),
                iphone.getId()
            ));

            // assert
            assertThat(result)
                .extracting(ProductListInfo::id)
                .containsExactlyInAnyOrder(iphone.getId(), proMax.getId());
            assertThat(result).allSatisfy(product -> {
                assertThat(product.brand().id()).isEqualTo(brand.getId());
                assertThat(product.likeCount()).isZero();
            });
        }
    }

    private Product createProduct(Brand brand, String name, long price) {
        Product product = productService.createProduct(
            brand.getId(),
            name,
            "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰",
            price
        );
        productLikeSummaryWriter.initialize(product.getId(), brand.getId());
        return product;
    }
}
