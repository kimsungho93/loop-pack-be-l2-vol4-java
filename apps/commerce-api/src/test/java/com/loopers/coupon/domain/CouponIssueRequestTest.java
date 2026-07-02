package com.loopers.coupon.domain;

import com.loopers.shared.error.CoreException;
import com.loopers.shared.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class CouponIssueRequestTest {

    private static final Long USER_ID = 101L;
    private static final Long COUPON_TEMPLATE_ID = 11L;
    private static final Long USER_COUPON_ID = 1001L;
    private static final ZonedDateTime REQUESTED_AT = ZonedDateTime.parse("2026-07-03T10:00:00+09:00");
    private static final ZonedDateTime PROCESSED_AT = ZonedDateTime.parse("2026-07-03T10:00:01+09:00");

    @DisplayName("발급 요청을 생성하면, REQUESTED 상태로 시작하고 처리 정보는 비어 있다.")
    @Test
    void startsAsRequested_whenCreated() {
        // arrange & act
        CouponIssueRequest request = CouponIssueRequest.request(USER_ID, COUPON_TEMPLATE_ID, REQUESTED_AT);

        // assert
        assertAll(
            () -> assertThat(request.getStatus()).isEqualTo(CouponIssueRequestStatus.REQUESTED),
            () -> assertThat(request.getUserId()).isEqualTo(USER_ID),
            () -> assertThat(request.getCouponTemplateId()).isEqualTo(COUPON_TEMPLATE_ID),
            () -> assertThat(request.getRequestedAt()).isEqualTo(REQUESTED_AT),
            () -> assertThat(request.getUserCouponId()).isNull(),
            () -> assertThat(request.getReason()).isNull(),
            () -> assertThat(request.getProcessedAt()).isNull(),
            () -> assertThat(request.isProcessed()).isFalse()
        );
    }

    @DisplayName("요청 일시가 비어 있으면, BAD_REQUEST 예외를 던진다.")
    @Test
    void throwsBadRequest_whenRequestedAtIsNull() {
        // arrange
        ZonedDateTime requestedAt = null;

        // act & assert
        assertThatThrownBy(() -> CouponIssueRequest.request(USER_ID, COUPON_TEMPLATE_ID, requestedAt))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("발급 완료로 전이하면, ISSUED 상태와 발급 쿠폰 ID, 처리 일시가 기록된다.")
    @Test
    void marksIssued_withUserCouponIdAndProcessedAt() {
        // arrange
        CouponIssueRequest request = CouponIssueRequest.request(USER_ID, COUPON_TEMPLATE_ID, REQUESTED_AT);

        // act
        request.markIssued(USER_COUPON_ID, PROCESSED_AT);

        // assert
        assertAll(
            () -> assertThat(request.getStatus()).isEqualTo(CouponIssueRequestStatus.ISSUED),
            () -> assertThat(request.getUserCouponId()).isEqualTo(USER_COUPON_ID),
            () -> assertThat(request.getProcessedAt()).isEqualTo(PROCESSED_AT),
            () -> assertThat(request.isProcessed()).isTrue()
        );
    }

    @DisplayName("매진으로 전이하면, SOLD_OUT 상태와 처리 일시가 기록된다.")
    @Test
    void marksSoldOut_withProcessedAt() {
        // arrange
        CouponIssueRequest request = CouponIssueRequest.request(USER_ID, COUPON_TEMPLATE_ID, REQUESTED_AT);

        // act
        request.markSoldOut(PROCESSED_AT);

        // assert
        assertAll(
            () -> assertThat(request.getStatus()).isEqualTo(CouponIssueRequestStatus.SOLD_OUT),
            () -> assertThat(request.getProcessedAt()).isEqualTo(PROCESSED_AT),
            () -> assertThat(request.isProcessed()).isTrue()
        );
    }

    @DisplayName("기발급으로 전이하면, ALREADY_ISSUED 상태와 기존 발급 쿠폰 ID가 기록된다.")
    @Test
    void marksAlreadyIssued_withExistingUserCouponId() {
        // arrange
        CouponIssueRequest request = CouponIssueRequest.request(USER_ID, COUPON_TEMPLATE_ID, REQUESTED_AT);

        // act
        request.markAlreadyIssued(USER_COUPON_ID, PROCESSED_AT);

        // assert
        assertAll(
            () -> assertThat(request.getStatus()).isEqualTo(CouponIssueRequestStatus.ALREADY_ISSUED),
            () -> assertThat(request.getUserCouponId()).isEqualTo(USER_COUPON_ID),
            () -> assertThat(request.isProcessed()).isTrue()
        );
    }

    @DisplayName("실패로 전이하면, FAILED 상태와 실패 사유가 기록된다.")
    @Test
    void marksFailed_withReason() {
        // arrange
        CouponIssueRequest request = CouponIssueRequest.request(USER_ID, COUPON_TEMPLATE_ID, REQUESTED_AT);

        // act
        request.markFailed("이벤트 발행 실패", PROCESSED_AT);

        // assert
        assertAll(
            () -> assertThat(request.getStatus()).isEqualTo(CouponIssueRequestStatus.FAILED),
            () -> assertThat(request.getReason()).isEqualTo("이벤트 발행 실패"),
            () -> assertThat(request.isProcessed()).isTrue()
        );
    }

    @DisplayName("이미 처리된 요청을 다시 전이하면, CONFLICT 예외를 던진다.")
    @Test
    void throwsConflict_whenTransitioningProcessedRequest() {
        // arrange
        CouponIssueRequest request = CouponIssueRequest.request(USER_ID, COUPON_TEMPLATE_ID, REQUESTED_AT);
        request.markSoldOut(PROCESSED_AT);

        // act & assert
        assertThatThrownBy(() -> request.markIssued(USER_COUPON_ID, PROCESSED_AT))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.CONFLICT);
    }

    @DisplayName("요청 소유자가 아닌 사용자는, 소유자 확인에 실패한다.")
    @Test
    void checksOwner_byUserId() {
        // arrange
        CouponIssueRequest request = CouponIssueRequest.request(USER_ID, COUPON_TEMPLATE_ID, REQUESTED_AT);

        // act & assert
        assertAll(
            () -> assertThat(request.isRequestedBy(USER_ID)).isTrue(),
            () -> assertThat(request.isRequestedBy(999L)).isFalse()
        );
    }
}
