package com.loopers.catalog.infrastructure;

import com.loopers.catalog.application.CatalogEventOutboxRelayItem;
import com.loopers.catalog.application.CatalogEventOutboxRelayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

@RequiredArgsConstructor
@Component
public class CatalogEventOutboxRelayRepositoryImpl implements CatalogEventOutboxRelayRepository {

    private final CatalogEventOutboxJpaRepository catalogEventOutboxJpaRepository;

    @Override
    @Transactional(readOnly = true)
    public List<CatalogEventOutboxRelayItem> findPending(ZonedDateTime now, int limit) {
        return catalogEventOutboxJpaRepository.findPendingForRelay(
                CatalogEventOutboxStatus.PENDING,
                now,
                PageRequest.of(0, limit)
            ).stream()
            .map(this::toRelayItem)
            .toList();
    }

    @Override
    @Transactional
    public void markPublished(Long outboxId, ZonedDateTime publishedAt) {
        CatalogEventOutbox outbox = findRequired(outboxId);
        outbox.markPublished(publishedAt);
    }

    @Override
    @Transactional
    public void markPublishFailed(Long outboxId, String reason, int maxRetryCount, ZonedDateTime failedAt) {
        CatalogEventOutbox outbox = findRequired(outboxId);
        outbox.markPublishFailed(reason, maxRetryCount, failedAt);
    }

    private CatalogEventOutbox findRequired(Long outboxId) {
        return catalogEventOutboxJpaRepository.findById(outboxId)
            .orElseThrow(() -> new IllegalStateException("Catalog event outbox not found. outboxId=" + outboxId));
    }

    private CatalogEventOutboxRelayItem toRelayItem(CatalogEventOutbox outbox) {
        return new CatalogEventOutboxRelayItem(
            outbox.getId(),
            outbox.getEventId(),
            outbox.getEventType(),
            outbox.getAggregateType(),
            outbox.getAggregateId(),
            outbox.getPartitionKey(),
            outbox.getPayload(),
            outbox.getOccurredAt()
        );
    }
}
