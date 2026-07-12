package com.loopers.queue.interfaces.gate;

import com.loopers.shared.error.CoreException;
import com.loopers.shared.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 주문 동시 실행을 상한으로 잘라, 주문 폭주가 워커 스레드 전체를 점거해
 * 다른 API까지 마비시키는 것(침수 전파)을 막는 격벽.
 * 토큰 소비(게이트)보다 먼저 실행해 부작용 없는 검사를 앞에 둔다.
 */
@RequiredArgsConstructor
@Component
public class OrderBulkheadInterceptor implements HandlerInterceptor {

    // 비 POST 요청은 세마포어를 얻지 않으므로, 얻은 요청만 해제하도록 표식을 남긴다.
    private static final String ACQUIRED_ATTRIBUTE = OrderBulkheadInterceptor.class.getName() + ".acquired";

    private final OrderBulkhead orderBulkhead;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!"POST".equals(request.getMethod())) {
            return true;
        }
        if (!orderBulkhead.tryEnter()) {
            throw new CoreException(ErrorType.TOO_MANY_REQUESTS, "주문이 몰려 있습니다. 잠시 후 다시 시도해주세요.");
        }
        request.setAttribute(ACQUIRED_ATTRIBUTE, Boolean.TRUE);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (Boolean.TRUE.equals(request.getAttribute(ACQUIRED_ATTRIBUTE))) {
            orderBulkhead.exit();
        }
    }
}
