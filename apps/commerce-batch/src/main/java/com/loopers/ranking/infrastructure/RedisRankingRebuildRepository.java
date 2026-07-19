package com.loopers.ranking.infrastructure;

import com.loopers.config.redis.RedisConfig;
import com.loopers.ranking.RankingExpirationPolicy;
import com.loopers.ranking.RankingRedisKey;
import com.loopers.ranking.application.RankingRebuildRepository;
import com.loopers.ranking.application.RankingRebuildScore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;

@Component
public class RedisRankingRebuildRepository implements RankingRebuildRepository {

    private static final int WRITE_BATCH_SIZE = 1_000;

    private final RedisTemplate<String, String> masterRedisTemplate;

    public RedisRankingRebuildRepository(
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> masterRedisTemplate
    ) {
        this.masterRedisTemplate = masterRedisTemplate;
    }

    @Override
    public long replace(LocalDate rankingDate, long runId, List<RankingRebuildScore> scores) {
        String targetKey = RankingRedisKey.daily(rankingDate);
        String tempKey = RankingRedisKey.rebuildTemp(rankingDate, runId);
        List<ZSetOperations.TypedTuple<String>> entries = scores.stream()
            .map(this::toEntry)
            .toList();

        masterRedisTemplate.delete(tempKey);
        if (entries.isEmpty()) {
            masterRedisTemplate.delete(targetKey);
            return 0;
        }

        try {
            writeInBatches(tempKey, entries);
            long rebuiltCount = validateRebuiltCount(tempKey, entries.size());
            applyExpiration(tempKey, rankingDate);
            masterRedisTemplate.rename(tempKey, targetKey);
            return rebuiltCount;
        } catch (RuntimeException exception) {
            cleanUpTemp(tempKey, exception);
            throw exception;
        }
    }

    private ZSetOperations.TypedTuple<String> toEntry(RankingRebuildScore score) {
        return new DefaultTypedTuple<>(score.productId().toString(), score.score());
    }

    private void writeInBatches(String tempKey, List<ZSetOperations.TypedTuple<String>> entries) {
        for (int start = 0; start < entries.size(); start += WRITE_BATCH_SIZE) {
            int end = Math.min(start + WRITE_BATCH_SIZE, entries.size());
            masterRedisTemplate.opsForZSet().add(
                tempKey,
                new LinkedHashSet<>(entries.subList(start, end))
            );
        }
    }

    private long validateRebuiltCount(String tempKey, int expectedCount) {
        Long rebuiltCount = masterRedisTemplate.opsForZSet().zCard(tempKey);
        if (rebuiltCount == null || rebuiltCount != expectedCount) {
            throw new IllegalStateException("Ranking rebuild member count does not match scores");
        }
        return rebuiltCount;
    }

    private void applyExpiration(String tempKey, LocalDate rankingDate) {
        Boolean expirationApplied = masterRedisTemplate.expireAt(
            tempKey,
            Date.from(RankingExpirationPolicy.expiresAt(rankingDate))
        );
        if (!Boolean.TRUE.equals(expirationApplied)) {
            throw new IllegalStateException("Failed to apply Ranking rebuild expiration");
        }
    }

    private void cleanUpTemp(String tempKey, RuntimeException originalException) {
        try {
            masterRedisTemplate.delete(tempKey);
        } catch (RuntimeException cleanupException) {
            originalException.addSuppressed(cleanupException);
        }
    }
}
