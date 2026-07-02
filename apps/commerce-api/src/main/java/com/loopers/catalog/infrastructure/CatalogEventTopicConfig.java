package com.loopers.catalog.infrastructure;

import com.loopers.catalog.application.CatalogEventOutboxRelayProperties;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.TopicBuilder;

@RequiredArgsConstructor
@Profile("local")
@Configuration
public class CatalogEventTopicConfig {

    private static final int PARTITION_COUNT = 3;
    private static final int REPLICATION_FACTOR = 1;

    private final CatalogEventOutboxRelayProperties properties;

    @Bean
    public NewTopic catalogEventsTopic() {
        return TopicBuilder.name(properties.topicName())
            .partitions(PARTITION_COUNT)
            .replicas(REPLICATION_FACTOR)
            .build();
    }
}
