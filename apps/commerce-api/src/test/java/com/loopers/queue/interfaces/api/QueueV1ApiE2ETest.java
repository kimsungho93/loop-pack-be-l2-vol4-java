package com.loopers.queue.interfaces.api;

import com.loopers.queue.application.QueueAdmitter;
import com.loopers.queue.domain.QueueEntryStatus;
import com.loopers.shared.presentation.ApiResponse;
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
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QueueV1ApiE2ETest {

    private static final String ENDPOINT_USERS = "/api/v1/users";
    private static final String ENDPOINT_ENTER = "/api/v1/queue/enter";
    private static final String ENDPOINT_POSITION = "/api/v1/queue/position";
    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";
    private static final String LOGIN_ID = "loopers01";
    private static final String OTHER_LOGIN_ID = "loopers02";
    private static final String PASSWORD = "Loopers!2026";

    private final TestRestTemplate testRestTemplate;
    private final QueueAdmitter queueAdmitter;
    private final DatabaseCleanUp databaseCleanUp;
    private final RedisCleanUp redisCleanUp;

    @Autowired
    QueueV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        QueueAdmitter queueAdmitter,
        DatabaseCleanUp databaseCleanUp,
        RedisCleanUp redisCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
        this.redisCleanUp = redisCleanUp;
        this.queueAdmitter = queueAdmitter;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @DisplayName("POST /api/v1/queue/enter")
    @Nested
    class Enter {

        @DisplayName("대기열에 진입하면, 200 OK와 WAITING 상태, 배정된 순번, 대기 토큰을 반환한다.")
        @Test
        void returnsWaitingWithPosition_whenUserEnters() {
            // arrange
            signUpUser(LOGIN_ID);

            // act
            ResponseEntity<ApiResponse<QueueV1Dto.EnterResponse>> response = enter(authHeaders(LOGIN_ID));

            // assert
            QueueV1Dto.EnterResponse data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.status()).isEqualTo(QueueEntryStatus.WAITING),
                () -> assertThat(data.position()).isEqualTo(1L),
                () -> assertThat(data.totalWaiting()).isEqualTo(1L),
                () -> assertThat(data.waitingToken()).isNotBlank()
            );
        }

        @DisplayName("이미 줄에 선 사용자가 다시 진입해도, 기존 순번이 유지된다.")
        @Test
        void keepsOriginalPosition_whenUserEntersAgain() {
            // arrange
            signUpUser(LOGIN_ID);
            signUpUser(OTHER_LOGIN_ID);
            enter(authHeaders(LOGIN_ID));
            enter(authHeaders(OTHER_LOGIN_ID));

            // act
            ResponseEntity<ApiResponse<QueueV1Dto.EnterResponse>> response = enter(authHeaders(LOGIN_ID));

            // assert
            QueueV1Dto.EnterResponse data = response.getBody().data();
            assertAll(
                () -> assertThat(data.position()).isEqualTo(1L),
                () -> assertThat(data.totalWaiting()).isEqualTo(2L)
            );
        }

        @DisplayName("인증 헤더가 없으면, 401 UNAUTHORIZED를 반환한다.")
        @Test
        void returnsUnauthorized_whenAuthenticationHeadersAreMissing() {
            // act
            ResponseEntity<ApiResponse<QueueV1Dto.EnterResponse>> response = enter(new HttpHeaders());

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("GET /api/v1/queue/position")
    @Nested
    class GetPosition {

        @DisplayName("인증 없이 대기 토큰만으로, WAITING 상태와 순번, 예상 대기 시간, 다음 폴링 간격을 조회한다.")
        @Test
        void returnsWaitingWithEstimate_whenPolledWithWaitingTokenOnly() {
            // arrange
            signUpUser(LOGIN_ID);
            String waitingToken = enter(authHeaders(LOGIN_ID)).getBody().data().waitingToken();

            // act — 인증 헤더 없이 대기 토큰만 보낸다.
            ResponseEntity<ApiResponse<QueueV1Dto.PositionResponse>> response = getPosition(waitingToken);

            // assert
            QueueV1Dto.PositionResponse data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.status()).isEqualTo(QueueEntryStatus.WAITING),
                () -> assertThat(data.position()).isEqualTo(1L),
                () -> assertThat(data.estimatedWaitSeconds()).isPositive(),
                () -> assertThat(data.pollAfterSeconds()).isGreaterThanOrEqualTo(3L),
                () -> assertThat(response.getHeaders().getFirst("Retry-After"))
                    .isEqualTo(String.valueOf(data.pollAfterSeconds())),
                () -> assertThat(data.token()).isNull()
            );
        }

        @DisplayName("입장이 완료되면, READY 상태와 입장 토큰을 반환하고 폴링 간격은 없다.")
        @Test
        void returnsReadyWithToken_whenUserIsAdmitted() {
            // arrange
            signUpUser(LOGIN_ID);
            String waitingToken = enter(authHeaders(LOGIN_ID)).getBody().data().waitingToken();
            queueAdmitter.admitNextBatch();

            // act
            ResponseEntity<ApiResponse<QueueV1Dto.PositionResponse>> response = getPosition(waitingToken);

            // assert — 폴링 중단 신호: pollAfterSeconds 도 Retry-After 헤더도 없어야 한다.
            QueueV1Dto.PositionResponse data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.status()).isEqualTo(QueueEntryStatus.READY),
                () -> assertThat(data.token()).isNotBlank(),
                () -> assertThat(data.pollAfterSeconds()).isNull(),
                () -> assertThat(response.getHeaders().getFirst("Retry-After")).isNull()
            );
        }

        @DisplayName("알 수 없는 대기 토큰이면, 에러가 아닌 200 OK와 EXPIRED 상태를 반환한다.")
        @Test
        void returnsExpiredWithOkStatus_whenWaitingTokenIsUnknown() {
            // act
            ResponseEntity<ApiResponse<QueueV1Dto.PositionResponse>> response = getPosition("unknown-waiting-token");

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().status()).isEqualTo(QueueEntryStatus.EXPIRED)
            );
        }

        @DisplayName("대기 토큰 헤더가 없으면, 400 BAD_REQUEST를 반환한다.")
        @Test
        void returnsBadRequest_whenWaitingTokenHeaderIsMissing() {
            // act
            ResponseEntity<ApiResponse<QueueV1Dto.PositionResponse>> response = testRestTemplate.exchange(
                ENDPOINT_POSITION, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    private ResponseEntity<ApiResponse<QueueV1Dto.EnterResponse>> enter(HttpHeaders headers) {
        ParameterizedTypeReference<ApiResponse<QueueV1Dto.EnterResponse>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(ENDPOINT_ENTER, HttpMethod.POST, new HttpEntity<>(headers), responseType);
    }

    private ResponseEntity<ApiResponse<QueueV1Dto.PositionResponse>> getPosition(String waitingToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(QueueV1Controller.WAITING_TOKEN_HEADER, waitingToken);
        ParameterizedTypeReference<ApiResponse<QueueV1Dto.PositionResponse>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(ENDPOINT_POSITION, HttpMethod.GET, new HttpEntity<>(headers), responseType);
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
