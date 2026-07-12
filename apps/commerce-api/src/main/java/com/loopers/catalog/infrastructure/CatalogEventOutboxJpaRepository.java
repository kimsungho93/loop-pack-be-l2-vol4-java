package com.loopers.catalog.infrastructure;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;

public interface CatalogEventOutboxJpaRepository extends JpaRepository<CatalogEventOutbox, Long> {

    @Query("""
        select outbox
        from CatalogEventOutbox outbox
        where outbox.status = :status
          and outbox.nextRetryAt <= :now
        order by outbox.id asc
        """)
    List<CatalogEventOutbox> findPendingForRelay(
        @Param("status") CatalogEventOutboxStatus status,
        @Param("now") ZonedDateTime now,
        Pageable pageable
    );
}
