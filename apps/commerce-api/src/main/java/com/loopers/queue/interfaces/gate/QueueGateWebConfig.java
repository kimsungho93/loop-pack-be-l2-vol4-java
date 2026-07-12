package com.loopers.queue.interfaces.gate;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@RequiredArgsConstructor
@Configuration
@ConditionalOnProperty(
    name = "commerce.queue.order-gate-enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class QueueGateWebConfig implements WebMvcConfigurer {

    private final QueueGateInterceptor queueGateInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(queueGateInterceptor).addPathPatterns("/api/v1/orders").order(1);
    }
}
