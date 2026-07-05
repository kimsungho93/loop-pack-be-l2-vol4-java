package com.loopers.queue.interfaces.api;

import com.loopers.queue.application.QueueFacade;
import com.loopers.shared.presentation.ApiResponse;
import lombok.RequiredArgsConstructor;
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
    public ApiResponse<QueueV1Dto.PositionResponse> enter(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(QueueV1Dto.PositionResponse.from(queueFacade.enter(userId)));
    }

    // WAITING/READY/EXPIRED 는 모두 정상 비즈니스 상태이므로 HTTP 에러코드가 아닌 status 필드로 구분한다.
    @GetMapping("/position")
    public ApiResponse<QueueV1Dto.PositionResponse> getPosition(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(QueueV1Dto.PositionResponse.from(queueFacade.getPosition(userId)));
    }
}
