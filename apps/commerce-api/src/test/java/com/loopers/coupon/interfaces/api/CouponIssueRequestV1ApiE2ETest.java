package com.loopers.coupon.interfaces.api;

import com.loopers.coupon.application.CouponIssueRequestMessage;
import com.loopers.coupon.application.CouponIssueRequestPublisher;
import com.loopers.coupon.domain.CouponIssueRequestService;
import com.loopers.coupon.domain.CouponIssueRequestStatus;
import com.loopers.coupon.domain.CouponTemplate;
import com.loopers.coupon.domain.CouponTemplateRepository;
import com.loopers.coupon.domain.CouponType;
import com.loopers.coupon.domain.policy.FixedCouponDiscountPolicy;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponIssueRequestV1ApiE2ETest {

    private static final String ENDPOINT_USERS = "/api/v1/users";
    private static final String ENDPOINT_ISSUE_REQUEST = "/api/v1/coupons/{couponId}/issue-requests";
    private static final String ENDPOINT_GET_ISSUE_REQUEST = "/api/v1/coupons/issue-requests/{requestId}";
    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";
    private static final String LOGIN_ID = "loopers01";
    private static final String OTHER_LOGIN_ID = "loopers02";
    private static final String PASSWORD = "Loopers!2026";
    private static final String COUPON_NAME = "1주년 쿠폰";
    private static final ZonedDateTime EXPIRED_AT = ZonedDateTime.parse("2026-12-31T23:59:59+09:00");
    private static final FixedCouponDiscountPolicy FIXED_POLICY = new FixedCouponDiscountPolicy();

    private final TestRestTemplate testRestTemplate;
    private final CouponTemplateRepository couponTemplateRepository;
    private final CouponIssueRequestService couponIssueRequestService;
    private final DatabaseCleanUp databaseCleanUp;
    private final RedisCleanUp redisCleanUp;

    @MockitoBean
    private CouponIssueRequestPublisher couponIssueRequestPublisher;

    @Autowired
    CouponIssueRequestV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        CouponTemplateRepository couponTemplateRepository,
        CouponIssueRequestService couponIssueRequestService,
        DatabaseCleanUp databaseCleanUp,
        RedisCleanUp redisCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.couponTemplateRepository = couponTemplateRepository;
        this.couponIssueRequestService = couponIssueRequestService;
        this.databaseCleanUp = databaseCleanUp;
        this.redisCleanUp = redisCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @DisplayName("POST /api/v1/coupons/{couponId}/issue-requests")
    @Nested
    class RequestIssueCoupon {

        @DisplayName("한정 수량 쿠폰에 발급을 요청하면, 202 ACCEPTED와 REQUESTED 상태의 요청 정보를 반환한다.")
        @Test
        void returnsAcceptedRequest_whenLimitedCouponIsRequested() {
            // arrange
            signUpUser(LOGIN_ID);
            CouponTemplate couponTemplate = createLimitedCouponTemplate(100);

            // act
            ResponseEntity<ApiResponse<CouponV1Dto.CouponIssueRequestResponse>> response =
                requestIssue(couponTemplate.getId(), authHeaders(LOGIN_ID));

            // assert
            CouponV1Dto.CouponIssueRequestResponse data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED),
                () -> assertThat(data.requestId()).isNotNull(),
                () -> assertThat(data.status()).isEqualTo(CouponIssueRequestStatus.REQUESTED),
                () -> verify(couponIssueRequestPublisher).publish(any(CouponIssueRequestMessage.class))
            );
        }

        @DisplayName("수량 한정이 아닌 쿠폰에 발급을 요청하면, 400 BAD_REQUEST를 반환한다.")
        @Test
        void returnsBadRequest_whenCouponHasNoQuantityLimit() {
            // arrange
            signUpUser(LOGIN_ID);
            CouponTemplate couponTemplate = createLimitedCouponTemplate(null);

            // act
            ResponseEntity<ApiResponse<Object>> response =
                requestIssueForError(couponTemplate.getId(), authHeaders(LOGIN_ID));

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("같은 사용자가 다시 요청하면, 409 CONFLICT를 반환한다.")
        @Test
        void returnsConflict_whenUserRequestsAgain() {
            // arrange
            signUpUser(LOGIN_ID);
            CouponTemplate couponTemplate = createLimitedCouponTemplate(100);
            requestIssue(couponTemplate.getId(), authHeaders(LOGIN_ID));

            // act
            ResponseEntity<ApiResponse<Object>> response =
                requestIssueForError(couponTemplate.getId(), authHeaders(LOGIN_ID));

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @DisplayName("수량이 모두 소진된 쿠폰에 요청하면, 409 CONFLICT를 반환한다.")
        @Test
        void returnsConflict_whenCouponIsSoldOut() {
            // arrange
            signUpUser(LOGIN_ID);
            signUpUser(OTHER_LOGIN_ID);
            CouponTemplate couponTemplate = createLimitedCouponTemplate(1);
            requestIssue(couponTemplate.getId(), authHeaders(LOGIN_ID));

            // act
            ResponseEntity<ApiResponse<Object>> response =
                requestIssueForError(couponTemplate.getId(), authHeaders(OTHER_LOGIN_ID));

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @DisplayName("인증 헤더가 없으면, 401 UNAUTHORIZED를 반환한다.")
        @Test
        void returnsUnauthorized_whenAuthenticationHeadersAreMissing() {
            // arrange
            CouponTemplate couponTemplate = createLimitedCouponTemplate(100);

            // act
            ResponseEntity<ApiResponse<Object>> response =
                requestIssueForError(couponTemplate.getId(), new HttpHeaders());

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("GET /api/v1/coupons/issue-requests/{requestId}")
    @Nested
    class GetIssueRequest {

        @DisplayName("발급이 완료된 요청을 조회하면, ISSUED 상태와 발급 쿠폰 ID를 반환한다.")
        @Test
        void returnsIssuedResult_whenRequestIsProcessed() {
            // arrange
            signUpUser(LOGIN_ID);
            CouponTemplate couponTemplate = createLimitedCouponTemplate(100);
            Long requestId = requestIssue(couponTemplate.getId(), authHeaders(LOGIN_ID)).getBody().data().requestId();
            couponIssueRequestService.process(requestId);

            // act
            ResponseEntity<ApiResponse<CouponV1Dto.CouponIssueRequestResponse>> response =
                getIssueRequest(requestId, authHeaders(LOGIN_ID));

            // assert
            CouponV1Dto.CouponIssueRequestResponse data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.status()).isEqualTo(CouponIssueRequestStatus.ISSUED),
                () -> assertThat(data.userCouponId()).isNotNull(),
                () -> assertThat(data.processedAt()).isNotNull()
            );
        }

        @DisplayName("아직 처리되지 않은 요청을 조회하면, REQUESTED 상태를 반환한다.")
        @Test
        void returnsRequestedStatus_whenRequestIsNotProcessedYet() {
            // arrange
            signUpUser(LOGIN_ID);
            CouponTemplate couponTemplate = createLimitedCouponTemplate(100);
            Long requestId = requestIssue(couponTemplate.getId(), authHeaders(LOGIN_ID)).getBody().data().requestId();

            // act
            ResponseEntity<ApiResponse<CouponV1Dto.CouponIssueRequestResponse>> response =
                getIssueRequest(requestId, authHeaders(LOGIN_ID));

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().status()).isEqualTo(CouponIssueRequestStatus.REQUESTED)
            );
        }

        @DisplayName("다른 사용자의 요청을 조회하면, 403 FORBIDDEN을 반환한다.")
        @Test
        void returnsForbidden_whenRequestBelongsToAnotherUser() {
            // arrange
            signUpUser(LOGIN_ID);
            signUpUser(OTHER_LOGIN_ID);
            CouponTemplate couponTemplate = createLimitedCouponTemplate(100);
            Long requestId = requestIssue(couponTemplate.getId(), authHeaders(LOGIN_ID)).getBody().data().requestId();

            // act
            ResponseEntity<ApiResponse<Object>> response =
                getIssueRequestForError(requestId, authHeaders(OTHER_LOGIN_ID));

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @DisplayName("존재하지 않는 요청 ID로 조회하면, 404 NOT_FOUND를 반환한다.")
        @Test
        void returnsNotFound_whenRequestDoesNotExist() {
            // arrange
            signUpUser(LOGIN_ID);
            Long requestId = 999_999L;

            // act
            ResponseEntity<ApiResponse<Object>> response =
                getIssueRequestForError(requestId, authHeaders(LOGIN_ID));

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    private CouponTemplate createLimitedCouponTemplate(Integer totalQuantity) {
        return couponTemplateRepository.save(CouponTemplate.create(
            COUPON_NAME,
            CouponType.FIXED,
            2_000L,
            10_000L,
            totalQuantity,
            EXPIRED_AT,
            FIXED_POLICY
        ));
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

    private ResponseEntity<ApiResponse<CouponV1Dto.CouponIssueRequestResponse>> requestIssue(Long couponId, HttpHeaders headers) {
        ParameterizedTypeReference<ApiResponse<CouponV1Dto.CouponIssueRequestResponse>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            ENDPOINT_ISSUE_REQUEST,
            HttpMethod.POST,
            new HttpEntity<>(headers),
            responseType,
            couponId
        );
    }

    private ResponseEntity<ApiResponse<Object>> requestIssueForError(Long couponId, HttpHeaders headers) {
        ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            ENDPOINT_ISSUE_REQUEST,
            HttpMethod.POST,
            new HttpEntity<>(headers),
            responseType,
            couponId
        );
    }

    private ResponseEntity<ApiResponse<CouponV1Dto.CouponIssueRequestResponse>> getIssueRequest(Long requestId, HttpHeaders headers) {
        ParameterizedTypeReference<ApiResponse<CouponV1Dto.CouponIssueRequestResponse>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            ENDPOINT_GET_ISSUE_REQUEST,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            responseType,
            requestId
        );
    }

    private ResponseEntity<ApiResponse<Object>> getIssueRequestForError(Long requestId, HttpHeaders headers) {
        ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            ENDPOINT_GET_ISSUE_REQUEST,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            responseType,
            requestId
        );
    }

    private HttpHeaders authHeaders(String loginId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_LOGIN_ID, loginId);
        headers.set(HEADER_LOGIN_PW, PASSWORD);
        return headers;
    }
}
