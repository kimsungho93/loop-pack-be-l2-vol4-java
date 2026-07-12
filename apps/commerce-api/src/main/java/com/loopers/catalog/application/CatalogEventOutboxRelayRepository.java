package com.loopers.catalog.application;

import java.time.ZonedDateTime;
import java.util.List;

public interface CatalogEventOutboxRelayRepository {

    List<CatalogEventOutboxRelayItem> findPending(ZonedDateTime now, int limit);

    void markPublished(Long outboxId, ZonedDateTime publishedAt);

    void markPublishFailed(Long outboxId, String reason, int maxRetryCount, ZonedDateTime failedAt);
}
