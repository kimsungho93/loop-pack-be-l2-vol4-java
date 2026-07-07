package com.loopers.queue.interfaces.gate;

import com.loopers.queue.application.QueueMetrics;
import com.loopers.queue.application.QueueProperties;
import com.loopers.queue.application.TokenConsumeResult;
import com.loopers.queue.application.WaitingQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QueueGateInterceptorTest {

    private final QueueProperties properties = new QueueProperties(
        5, 100L, Duration.ofMinutes(5), Duration.ofMinutes(30), 16, new QueueProperties.Poll(0.15, 3L, 30L, 0));
    private final WaitingQueue waitingQueue = mock(WaitingQueue.class);
    private final QueueMetrics queueMetrics = mock(QueueMetrics.class);
    private final QueueGateInterceptor interceptor = new QueueGateInterceptor(waitingQueue, properties, queueMetrics);

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(101L, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @DisplayName("게이트 저장소가 판정하지 못하면(UNDECIDED), 요청을 통과시키고 fail-open 지표를 남긴다.")
    @Test
    void passesRequestAndRecordsFailOpen_whenConsumeIsUndecided() {
        // arrange
        when(waitingQueue.consumeToken(anyLong(), anyString(), any(Duration.class)))
            .thenReturn(TokenConsumeResult.UNDECIDED);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/orders");
        request.addHeader(QueueGateInterceptor.QUEUE_TOKEN_HEADER, "token-a");

        // act
        boolean passed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        // assert — 소비하지 않았으므로 복구 대상 표식도 남기지 않는다.
        assertAll(
            () -> assertThat(passed).isTrue(),
            () -> assertThat(request.getAttribute(QueueGateInterceptor.CONSUMED_TOKEN_ATTRIBUTE)).isNull(),
            () -> verify(queueMetrics).recordGateFailOpen()
        );
    }
}
