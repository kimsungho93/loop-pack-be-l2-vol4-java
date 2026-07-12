package com.loopers.metrics.infrastructure;

import com.loopers.confg.kafka.KafkaConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.TopicBuilder;

@Profile("local")
@Configuration
public class CatalogMetricsTopicConfig {

    private static final int PARTITION_COUNT = 3;
    private static final int REPLICATION_FACTOR = 1;

    @Bean
    public NewTopic catalogEventsTopic(
        @Value("${commerce.metrics.catalog.topic-name:catalog-events}") String topicName
    ) {
        return TopicBuilder.name(topicName)
            .partitions(PARTITION_COUNT)
            .replicas(REPLICATION_FACTOR)
            .build();
    }

    @Bean
    public NewTopic catalogEventsDltTopic(
        @Value("${commerce.metrics.catalog.topic-name:catalog-events}") String topicName
    ) {
        return TopicBuilder.name(topicName + KafkaConfig.DLT_SUFFIX)
            .partitions(PARTITION_COUNT)
            .replicas(REPLICATION_FACTOR)
            .build();
    }
}
