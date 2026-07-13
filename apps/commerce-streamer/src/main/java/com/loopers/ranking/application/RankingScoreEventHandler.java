package com.loopers.ranking.application;

import com.loopers.metrics.application.CatalogEventEnvelope;
import com.loopers.ranking.RankingExpirationPolicy;
import com.loopers.ranking.RankingScorePolicy;
import com.loopers.ranking.RankingWindow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Service
public class RankingScoreEventHandler {

    private final RankingScoreRepository rankingScoreRepository;
    private final RankingScorePolicy scorePolicy;
    private final Clock clock;

    public void handle(List<CatalogEventEnvelope> events) {
        Instant handledAt = clock.instant();
        Map<RankingScoreGroup, List<RankingScoreDelta>> groupedDeltas = new LinkedHashMap<>();
        for (CatalogEventEnvelope event : events) {
            RankingWindow window = RankingWindow.from(event.occurredAt());
            if (isExpired(window, handledAt)) {
                continue;
            }

            RankingScoreGroup group = new RankingScoreGroup(window, event.payload().productId());
            groupedDeltas.computeIfAbsent(group, ignored -> new ArrayList<>())
                .add(new RankingScoreDelta(event.eventId(), scoreDelta(event)));
        }

        groupedDeltas.forEach((group, deltas) -> rankingScoreRepository.apply(
            new RankingScoreBatch(group.window(), group.productId(), deltas)
        ));
    }

    private boolean isExpired(RankingWindow window, Instant handledAt) {
        Instant expiresAt = RankingExpirationPolicy.expiresAt(window.date());
        return !handledAt.isBefore(expiresAt);
    }

    private double scoreDelta(CatalogEventEnvelope event) {
        return switch (event.eventType()) {
            case PRODUCT_VIEWED -> scorePolicy.viewScore();
            case PRODUCT_LIKED, PRODUCT_UNLIKED -> scorePolicy.likeScore(requiredDelta(event));
        };
    }

    private long requiredDelta(CatalogEventEnvelope event) {
        Integer delta = event.payload().delta();
        if (delta == null) {
            throw new IllegalArgumentException("delta must not be null for like ranking event");
        }
        return delta;
    }

    private record RankingScoreGroup(RankingWindow window, long productId) {
    }
}
