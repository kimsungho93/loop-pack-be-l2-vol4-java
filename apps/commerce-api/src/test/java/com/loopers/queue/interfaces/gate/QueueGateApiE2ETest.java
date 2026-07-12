package com.loopers.queue.interfaces.gate;

import com.loopers.brand.domain.Brand;
import com.loopers.brand.domain.BrandService;
import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductService;
import com.loopers.queue.application.QueueAdmitter;
import com.loopers.queue.application.QueueFacade;
import com.loopers.queue.domain.QueueEntryStatus;
import com.loopers.shared.presentation.ApiResponse;
import com.loopers.stock.domain.ProductStockService;
import com.loopers.user.domain.User;
import com.loopers.user.domain.UserRepository;
import com.loopers.user.domain.vo.LoginId;
import com.loopers.user.interfaces.api.UserV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "commerce.queue.order-gate-enabled=true"
)
class QueueGateApiE2ETest {

    private static final String ENDPOINT_USERS = "/api/v1/users";
    private static final String ENDPOINT_ORDERS = "/api/v1/orders";
    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";
    private static final String LOGIN_ID = "loopers01";
    private static final String PASSWORD = "Loopers!2026";

    private final TestRestTemplate testRestTemplate;
    private final QueueFacade queueFacade;
    private final QueueAdmitter queueAdmitter;
    private final UserRepository userRepository;
    private final DatabaseCleanUp databaseCleanUp;
    private final RedisCleanUp redisCleanUp;
    private final BrandService brandService;
    private final ProductService productService;
    private final ProductStockService productStockService;

    @Autowired
    QueueGateApiE2ETest(
        TestRestTemplate testRestTemplate,
        QueueFacade queueFacade,
        QueueAdmitter queueAdmitter,
        UserRepository userRepository,
        DatabaseCleanUp databaseCleanUp,
        RedisCleanUp redisCleanUp,
        BrandService brandService,
        ProductService productService,
        ProductStockService productStockService
    ) {
        this.testRestTemplate = testRestTemplate;
        this.queueFacade = queueFacade;
        this.queueAdmitter = queueAdmitter;
        this.userRepository = userRepository;
        this.databaseCleanUp = databaseCleanUp;
        this.redisCleanUp = redisCleanUp;
        this.brandService = brandService;
        this.productService = productService;
        this.productStockService = productStockService;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @DisplayName("POST /api/v1/orders (대기열 게이트)")
    @Nested
    class OrderGate {

        @DisplayName("입장 토큰 없이 주문하면, 429 TOO_MANY_REQUESTS를 반환한다.")
        @Test
        void returnsTooManyRequests_whenTokenIsMissing() {
            // arrange
            signUpUser(LOGIN_ID);

            // act
            ResponseEntity<ApiResponse<Object>> response = createOrder(authHeaders(LOGIN_ID));

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        }

        @DisplayName("유효한 입장 토큰이 있으면, 게이트를 통과한다.")
        @Test
        void passesGate_whenTokenIsValid() {
            // arrange
            signUpUser(LOGIN_ID);
            String token = admittedToken(LOGIN_ID);
            HttpHeaders headers = authHeaders(LOGIN_ID);
            headers.set(QueueGateInterceptor.QUEUE_TOKEN_HEADER, token);

            // act — 본문이 비어 있으므로 게이트를 통과했다면 429가 아닌 요청 검증 에러(400)가 난다.
            ResponseEntity<ApiResponse<Object>> response = createOrder(headers);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("주문 성공 후 같은 토큰으로 다시 주문하면, 409 CONFLICT 를 반환한다.")
        @Test
        void returnsConflict_whenTokenIsReusedAfterSuccessfulOrder() {
            // arrange
            signUpUser(LOGIN_ID);
            Brand brand = brandService.createBrand("애플", "기술과 디자인으로 일상을 새롭게 만드는 브랜드");
            Product product = productService.createProduct(brand.getId(), "아이폰 16 Pro", "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰", 1_550_000L);
            productStockService.createProductStock(product.getId(), 10);
            String token = admittedToken(LOGIN_ID);
            HttpHeaders headers = authHeaders(LOGIN_ID);
            headers.set(QueueGateInterceptor.QUEUE_TOKEN_HEADER, token);
            ResponseEntity<ApiResponse<Object>> first = createOrder(orderRequestBody(product.getId()), headers);

            // act
            ResponseEntity<ApiResponse<Object>> response = createOrder(orderRequestBody(product.getId()), headers);

            // assert
            assertAll(
                () -> assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT)
            );
        }

        @DisplayName("주문이 실패하면 토큰이 복구되어, 같은 토큰으로 다시 시도할 수 있다.")
        @Test
        void restoresToken_whenOrderFails() {
            // arrange — 빈 본문이라 게이트 통과 후 주문이 400 으로 실패한다.
            signUpUser(LOGIN_ID);
            String token = admittedToken(LOGIN_ID);
            HttpHeaders headers = authHeaders(LOGIN_ID);
            headers.set(QueueGateInterceptor.QUEUE_TOKEN_HEADER, token);
            ResponseEntity<ApiResponse<Object>> first = createOrder(headers);

            // act — 토큰이 복구됐다면 429 가 아니라 다시 400 이 난다.
            ResponseEntity<ApiResponse<Object>> response = createOrder(headers);

            // assert
            assertAll(
                () -> assertThat(first.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST)
            );
        }

        @DisplayName("주문 조회는 토큰 없이도 게이트를 통과한다.")
        @Test
        void passesGateWithoutToken_whenRequestIsNotPost() {
            // arrange
            signUpUser(LOGIN_ID);

            // act
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ENDPOINT_ORDERS,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(LOGIN_ID)),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    // 대기열 진입 → 입장 배치 실행 → READY 상태의 토큰을 얻는다.
    private String admittedToken(String loginId) {
        User user = userRepository.findByLoginId(LoginId.of(loginId)).orElseThrow();
        queueFacade.enter(user.getId());
        queueAdmitter.admitNextBatch();
        var position = queueFacade.getPosition(user.getId());
        assertAll(() -> assertThat(position.status()).isEqualTo(QueueEntryStatus.READY));
        return position.token();
    }

    private ResponseEntity<ApiResponse<Object>> createOrder(HttpHeaders headers) {
        headers.setContentType(MediaType.APPLICATION_JSON);
        ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(ENDPOINT_ORDERS, HttpMethod.POST, new HttpEntity<>("{}", headers), responseType);
    }

    private ResponseEntity<ApiResponse<Object>> createOrder(String body, HttpHeaders headers) {
        headers.setContentType(MediaType.APPLICATION_JSON);
        ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(ENDPOINT_ORDERS, HttpMethod.POST, new HttpEntity<>(body, headers), responseType);
    }

    private String orderRequestBody(Long productId) {
        return "{\"items\":[{\"productId\":%d,\"quantity\":1}]}".formatted(productId);
    }

    private void signUpUser(String loginId) {
        UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
            loginId,
            PASSWORD,
            "김상호",
            LocalDate.of(1993, 11, 3),
            loginId + "@example.com"
        );
        ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
        testRestTemplate.exchange(ENDPOINT_USERS, HttpMethod.POST, new HttpEntity<>(request), responseType);
    }

    private HttpHeaders authHeaders(String loginId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_LOGIN_ID, loginId);
        headers.set(HEADER_LOGIN_PW, PASSWORD);
        return headers;
    }
}
