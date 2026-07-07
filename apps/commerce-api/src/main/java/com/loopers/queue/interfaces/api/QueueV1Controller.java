package com.loopers.queue.interfaces.api;

import com.loopers.queue.application.QueueEnterInfo;
import com.loopers.queue.application.QueueFacade;
import com.loopers.queue.application.QueuePositionInfo;
import com.loopers.shared.presentation.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/queue")
public class QueueV1Controller {

    public static final String WAITING_TOKEN_HEADER = "X-Waiting-Token";

    private final QueueFacade queueFacade;

    // 진입은 1인 1자리(ZADD NX)와 토큰-사용자 바인딩을 위해 인증을 유지한다. 1인당 1회라 인증 비용도 무시 가능하다.
    @PostMapping("/enter")
    public ResponseEntity<ApiResponse<QueueV1Dto.EnterResponse>> enter(@AuthenticationPrincipal Long userId) {
        QueueEnterInfo info = queueFacade.enter(userId);
        return withRetryAfter(info.position().pollAfterSeconds())
            .body(ApiResponse.success(QueueV1Dto.EnterResponse.from(info)));
    }

    // 폴링은 요청량이 가장 많고 읽기 전용이라, 비밀번호 인증 대신 진입 시 발급한 대기 토큰으로 식별한다.
    // WAITING/READY/COMPLETED/EXPIRED 는 모두 정상 비즈니스 상태이므로 HTTP 에러코드가 아닌 status 필드로 구분한다.
    @GetMapping("/position")
    public ResponseEntity<ApiResponse<QueueV1Dto.PositionResponse>> getPosition(
        @RequestHeader(WAITING_TOKEN_HEADER) String waitingToken
    ) {
        QueuePositionInfo info = queueFacade.getPositionByWaitingToken(waitingToken);
        return withRetryAfter(info.pollAfterSeconds())
            .body(ApiResponse.success(QueueV1Dto.PositionResponse.from(info)));
    }

    private ResponseEntity.BodyBuilder withRetryAfter(Long pollAfterSeconds) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
        if (pollAfterSeconds != null) {
            builder.header("Retry-After", String.valueOf(pollAfterSeconds));
        }
        return builder;
    }
}
