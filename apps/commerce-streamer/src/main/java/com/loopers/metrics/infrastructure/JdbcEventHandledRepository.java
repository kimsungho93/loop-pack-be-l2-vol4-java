package com.loopers.metrics.infrastructure;

import com.loopers.metrics.application.CatalogEventEnvelope;
import com.loopers.metrics.application.EventHandledRepository;
import com.loopers.metrics.application.EventHandlingMetadata;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;

@RequiredArgsConstructor
@Component
public class JdbcEventHandledRepository implements EventHandledRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public boolean saveIfAbsent(CatalogEventEnvelope event, EventHandlingMetadata metadata, ZonedDateTime handledAt) {
        int inserted = jdbcTemplate.update("""
                insert ignore into event_handled(
                    event_id,
                    topic_name,
                    partition_no,
                    offset_no,
                    event_type,
                    aggregate_id,
                    handled_at
                )
                values (?, ?, ?, ?, ?, ?, ?)
                """,
            event.eventId(),
            metadata.topicName(),
            metadata.partitionNo(),
            metadata.offsetNo(),
            event.eventType().name(),
            event.aggregateId(),
            handledAt);

        return inserted == 1;
    }
}
