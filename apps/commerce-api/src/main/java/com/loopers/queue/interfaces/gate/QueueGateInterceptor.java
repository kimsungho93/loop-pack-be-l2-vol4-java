package com.loopers.queue.interfaces.gate;

import com.loopers.queue.application.QueueMetrics;
import com.loopers.queue.application.WaitingQueue;
import com.loopers.shared.error.CoreException;
import com.loopers.shared.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@RequiredArgsConstructor
@Component
public class QueueGateInterceptor implements HandlerInterceptor {

    public static final String QUEUE_TOKEN_HEADER = "X-Queue-Token";

    private final WaitingQueue waitingQueue;
    private final QueueMetrics queueMetrics;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!"POST".equals(request.getMethod())) {
            return true;
        }
        String token = request.getHeader(QUEUE_TOKEN_HEADER);
        if (token == null || token.isBlank() || !waitingQueue.consumeToken(currentUserId(), token)) {
            throw new CoreException(ErrorType.TOO_MANY_REQUESTS, "대기열 입장 토큰이 유효하지 않습니다. 대기열에 먼저 진입해주세요.");
        }
        queueMetrics.recordTokenConsumed();
        return true;
    }

    private long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (Long) authentication.getPrincipal();
    }
}
