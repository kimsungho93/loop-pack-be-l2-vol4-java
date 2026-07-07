package com.loopers.queue.interfaces.gate;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@RequiredArgsConstructor
@Configuration
@ConditionalOnProperty(
    name = "commerce.queue.order-bulkhead-enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class OrderBulkheadWebConfig implements WebMvcConfigurer {

    private final OrderBulkheadInterceptor orderBulkheadInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 게이트(토큰 소비, order 1)보다 먼저 실행해 벌크헤드 거절이 토큰을 낭비하지 않게 한다.
        registry.addInterceptor(orderBulkheadInterceptor).addPathPatterns("/api/v1/orders").order(0);
    }
}
