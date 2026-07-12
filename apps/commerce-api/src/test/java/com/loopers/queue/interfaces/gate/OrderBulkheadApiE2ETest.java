package com.loopers.queue.interfaces.gate;

import com.loopers.shared.presentation.ApiResponse;
import com.loopers.user.interfaces.api.UserV1Dto;
import com.loopers.utils.DatabaseCleanUp;
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

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "commerce.queue.order-bulkhead-enabled=true"
)
class OrderBulkheadApiE2ETest {

    private static final String ENDPOINT_USERS = "/api/v1/users";
    private static final String ENDPOINT_ORDERS = "/api/v1/orders";
    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";
    private static final String LOGIN_ID = "loopers01";
    private static final String PASSWORD = "Loopers!2026";

    private final TestRestTemplate testRestTemplate;
    private final OrderBulkhead orderBulkhead;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    OrderBulkheadApiE2ETest(
        TestRestTemplate testRestTemplate,
        OrderBulkhead orderBulkhead,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.orderBulkhead = orderBulkhead;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api/v1/orders (벌크헤드)")
    @Nested
    class Bulkhead {

        @DisplayName("동시 실행이 상한에 도달했으면, 429 TOO_MANY_REQUESTS로 즉시 거절한다.")
        @Test
        void returnsTooManyRequests_whenBulkheadIsFull() {
            // arrange — 상한(16)을 전부 선점해 가득 찬 상태를 만든다.
            signUpUser(LOGIN_ID);
            int acquired = 0;
            while (orderBulkhead.tryEnter()) {
                acquired++;
            }

            try {
                // act
                ResponseEntity<ApiResponse<Object>> response = createOrder(authHeaders(LOGIN_ID));

                // assert
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            } finally {
                for (int i = 0; i < acquired; i++) {
                    orderBulkhead.exit();
                }
            }
        }

        @DisplayName("여유가 생기면, 다시 벌크헤드를 통과한다.")
        @Test
        void passesBulkhead_whenCapacityIsAvailable() {
            // arrange
            signUpUser(LOGIN_ID);

            // act — 본문이 비어 있으므로 벌크헤드를 통과했다면 429가 아닌 요청 검증 에러(400)가 난다.
            ResponseEntity<ApiResponse<Object>> response = createOrder(authHeaders(LOGIN_ID));

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("주문 조회는 벌크헤드가 가득 차도 통과한다.")
        @Test
        void passesGetRequests_whenBulkheadIsFull() {
            // arrange
            signUpUser(LOGIN_ID);
            int acquired = 0;
            while (orderBulkhead.tryEnter()) {
                acquired++;
            }

            try {
                // act
                ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT_ORDERS,
                    HttpMethod.GET,
                    new HttpEntity<>(authHeaders(LOGIN_ID)),
                    new ParameterizedTypeReference<>() {}
                );

                // assert
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            } finally {
                for (int i = 0; i < acquired; i++) {
                    orderBulkhead.exit();
                }
            }
        }
    }

    private ResponseEntity<ApiResponse<Object>> createOrder(HttpHeaders headers) {
        headers.setContentType(MediaType.APPLICATION_JSON);
        ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(ENDPOINT_ORDERS, HttpMethod.POST, new HttpEntity<>("{}", headers), responseType);
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
