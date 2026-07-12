package com.loopers.metrics.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "event_handled",
    indexes = {
        @Index(name = "idx_event_handled_kafka_position", columnList = "topic_name, partition_no, offset_no")
    }
)
public class EventHandled {

    @Id
    @Column(name = "event_id", length = 36)
    private String eventId;

    @Column(name = "topic_name", nullable = false, length = 100)
    private String topicName;

    @Column(name = "partition_no", nullable = false)
    private int partitionNo;

    @Column(name = "offset_no", nullable = false)
    private long offsetNo;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId;

    @Column(name = "handled_at", nullable = false)
    private ZonedDateTime handledAt;
}
