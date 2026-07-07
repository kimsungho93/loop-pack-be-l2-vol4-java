package com.loopers.queue.interfaces.api;

import com.loopers.queue.application.QueueFacade;
import com.loopers.queue.application.QueuePositionInfo;
import com.loopers.shared.presentation.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/queue")
public class QueueV1Controller {

    private final QueueFacade queueFacade;

    @PostMapping("/enter")
    public ResponseEntity<ApiResponse<QueueV1Dto.PositionResponse>> enter(@AuthenticationPrincipal Long userId) {
        return toResponse(queueFacade.enter(userId));
    }

    // WAITING/READY/COMPLETED/EXPIRED 는 모두 정상 비즈니스 상태이므로 HTTP 에러코드가 아닌 status 필드로 구분한다.
    @GetMapping("/position")
    public ResponseEntity<ApiResponse<QueueV1Dto.PositionResponse>> getPosition(@AuthenticationPrincipal Long userId) {
        return toResponse(queueFacade.getPosition(userId));
    }

    private ResponseEntity<ApiResponse<QueueV1Dto.PositionResponse>> toResponse(QueuePositionInfo info) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
        if (info.pollAfterSeconds() != null) {
            builder.header("Retry-After", String.valueOf(info.pollAfterSeconds()));
        }
        return builder.body(ApiResponse.success(QueueV1Dto.PositionResponse.from(info)));
    }
}
