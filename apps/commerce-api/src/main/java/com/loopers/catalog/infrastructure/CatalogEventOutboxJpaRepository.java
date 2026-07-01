package com.loopers.catalog.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogEventOutboxJpaRepository extends JpaRepository<CatalogEventOutbox, Long> {
}
