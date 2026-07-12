package com.loopers.catalog.infrastructure;

import com.loopers.catalog.application.CatalogEventType;
import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "catalog_event_outbox",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_catalog_event_outbox_event_id", columnNames = "event_id")
    },
    indexes = {
        @Index(name = "idx_catalog_event_outbox_status_id", columnList = "status, id"),
        @Index(name = "idx_catalog_event_outbox_aggregate", columnList = "aggregate_type, aggregate_id")
    }
)
public class CatalogEventOutbox extends BaseEntity {

    private static final String PRODUCT_AGGREGATE_TYPE = "PRODUCT";

    @Column(name = "event_id", nullable = false, length = 36, updatable = false)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50, updatable = false)
    private CatalogEventType eventType;

    @Column(name = "aggregate_type", nullable = false, length = 30, updatable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private Long aggregateId;

    @Column(name = "partition_key", nullable = false, length = 100, updatable = false)
    private String partitionKey;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String payload;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private ZonedDateTime occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private CatalogEventOutboxStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_retry_at", nullable = false)
    private ZonedDateTime nextRetryAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "published_at")
    private ZonedDateTime publishedAt;

    private CatalogEventOutbox(
        String eventId,
        CatalogEventType eventType,
        Long aggregateId,
        String partitionKey,
        String payload,
        ZonedDateTime occurredAt
    ) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.aggregateType = PRODUCT_AGGREGATE_TYPE;
        this.aggregateId = aggregateId;
        this.partitionKey = partitionKey;
        this.payload = payload;
        this.occurredAt = occurredAt;
        this.status = CatalogEventOutboxStatus.PENDING;
        this.retryCount = 0;
        this.nextRetryAt = occurredAt;
    }

    public static CatalogEventOutbox pending(
        String eventId,
        CatalogEventType eventType,
        Long productId,
        String partitionKey,
        String payload,
        ZonedDateTime occurredAt
    ) {
        return new CatalogEventOutbox(eventId, eventType, productId, partitionKey, payload, occurredAt);
    }

    public void markPublished(ZonedDateTime publishedAt) {
        this.status = CatalogEventOutboxStatus.PUBLISHED;
        this.publishedAt = publishedAt;
        this.lastError = null;
    }

    public void markPublishFailed(String reason, int maxRetryCount, ZonedDateTime failedAt) {
        this.retryCount++;
        this.lastError = trim(reason);

        if (retryCount >= maxRetryCount) {
            this.status = CatalogEventOutboxStatus.RETRY_EXCEEDED;
            return;
        }

        this.status = CatalogEventOutboxStatus.PENDING;
        this.nextRetryAt = failedAt.plusSeconds(backoffSeconds());
    }

    @Override
    protected void guard() {
        if (!hasText(eventId)) {
            throw new IllegalStateException("eventId must not be blank");
        }
        if (eventType == null) {
            throw new IllegalStateException("eventType must not be null");
        }
        if (!hasText(aggregateType)) {
            throw new IllegalStateException("aggregateType must not be blank");
        }
        if (aggregateId == null) {
            throw new IllegalStateException("aggregateId must not be null");
        }
        if (!hasText(partitionKey)) {
            throw new IllegalStateException("partitionKey must not be blank");
        }
        if (!hasText(payload)) {
            throw new IllegalStateException("payload must not be blank");
        }
        if (occurredAt == null) {
            throw new IllegalStateException("occurredAt must not be null");
        }
        if (status == null) {
            throw new IllegalStateException("status must not be null");
        }
        if (retryCount < 0) {
            throw new IllegalStateException("retryCount must not be negative");
        }
        if (nextRetryAt == null) {
            throw new IllegalStateException("nextRetryAt must not be null");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String trim(String reason) {
        if (reason == null || reason.length() <= 500) {
            return reason;
        }
        return reason.substring(0, 500);
    }

    private long backoffSeconds() {
        return 1L << retryCount;
    }
}
