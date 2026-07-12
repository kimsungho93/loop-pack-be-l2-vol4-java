package com.loopers.queue.interfaces.gate;

import com.loopers.queue.application.QueueMetrics;
import com.loopers.queue.application.QueueProperties;
import com.loopers.queue.application.TokenConsumeResult;
import com.loopers.queue.application.WaitingQueue;
import com.loopers.shared.error.CoreException;
import com.loopers.shared.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@RequiredArgsConstructor
@Component
public class QueueGateInterceptor implements HandlerInterceptor {

    public static final String QUEUE_TOKEN_HEADER = "X-Queue-Token";

    // afterCompletion 에서 SecurityContext 에 의존하지 않도록 소비 시점의 사용자와 토큰을 요청에 보관한다.
    static final String CONSUMED_TOKEN_ATTRIBUTE = QueueGateInterceptor.class.getName() + ".CONSUMED_TOKEN";
    static final String CONSUMED_USER_ATTRIBUTE = QueueGateInterceptor.class.getName() + ".CONSUMED_USER";

    private final WaitingQueue waitingQueue;
    private final QueueProperties queueProperties;
    private final QueueMetrics queueMetrics;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!"POST".equals(request.getMethod())) {
            return true;
        }
        String token = request.getHeader(QUEUE_TOKEN_HEADER);
        if (token == null || token.isBlank()) {
            throw invalidTokenException();
        }
        long userId = currentUserId();
        TokenConsumeResult result = waitingQueue.consumeToken(userId, token, queueProperties.tokenTtl());
        return switch (result) {
            case CONSUMED -> {
                request.setAttribute(CONSUMED_TOKEN_ATTRIBUTE, token);
                request.setAttribute(CONSUMED_USER_ATTRIBUTE, userId);
                queueMetrics.recordTokenConsumed();
                yield true;
            }
            case ALREADY_USED -> throw new CoreException(ErrorType.CONFLICT, "이미 주문이 완료된 토큰입니다. 주문 내역을 확인해주세요.");
            case INVALID -> throw invalidTokenException();
            // 판정 불가(저장소 장애)는 fail-open — 대기열은 유일한 방어선이 아니다(벌크헤드·DB 가드가 받침).
            // 소비하지 않았으므로 복구 표식도 남기지 않는다.
            case UNDECIDED -> {
                log.warn("Queue gate fail-open: token store unavailable. userId={}", userId);
                queueMetrics.recordGateFailOpen();
                yield true;
            }
        };
    }

    // 토큰은 주문 생성 성공과 함께만 소진한다. 실패 응답이면 되돌려 사용자의 차례를 보존한다.
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        Object token = request.getAttribute(CONSUMED_TOKEN_ATTRIBUTE);
        Object userId = request.getAttribute(CONSUMED_USER_ATTRIBUTE);
        if (token == null || userId == null || response.getStatus() / 100 == 2) {
            return;
        }
        if (waitingQueue.restoreToken((Long) userId, (String) token, queueProperties.tokenTtl())) {
            queueMetrics.recordTokenRestored();
        }
    }

    private CoreException invalidTokenException() {
        return new CoreException(ErrorType.TOO_MANY_REQUESTS, "대기열 입장 토큰이 유효하지 않습니다. 대기열에 먼저 진입해주세요.");
    }

    private long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (Long) authentication.getPrincipal();
    }
}
