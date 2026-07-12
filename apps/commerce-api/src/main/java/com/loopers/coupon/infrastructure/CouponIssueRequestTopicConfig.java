package com.loopers.coupon.infrastructure;

import com.loopers.coupon.application.CouponIssueProperties;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.TopicBuilder;

// 운영 환경은 토픽을 인프라에서 관리한다(auto.create.topics.enable=false). local 브로커에만 자동 생성한다.
@Profile("local")
@Configuration
public class CouponIssueRequestTopicConfig {

    @Bean
    public NewTopic couponIssueRequestsTopic(CouponIssueProperties properties) {
        return TopicBuilder.name(properties.topicName())
            .partitions(3)
            .replicas(1)
            .build();
    }
}
