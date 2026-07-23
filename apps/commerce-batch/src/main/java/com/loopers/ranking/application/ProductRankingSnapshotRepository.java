package com.loopers.ranking.application;

import java.util.Optional;

public interface ProductRankingSnapshotRepository {

    Optional<ProductRankingSnapshotHeader> findBy(ProductRankingSnapshotKey key);

    void insert(NewProductRankingSnapshot snapshot);
}
