package com.loopers.ranking.infrastructure;

import com.loopers.config.redis.RedisConfig;
import com.loopers.ranking.RankingExpirationPolicy;
import com.loopers.ranking.RankingRedisKey;
import com.loopers.ranking.application.RankingCarryOverRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class RedisRankingCarryOverRepository implements RankingCarryOverRepository {

    private static final int WRITE_BATCH_SIZE = 1_000;

    private final RedisTemplate<String, String> masterRedisTemplate;

    public RedisRankingCarryOverRepository(
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> masterRedisTemplate
    ) {
        this.masterRedisTemplate = masterRedisTemplate;
    }

    @Override
    public long carryOver(LocalDate sourceDate, LocalDate targetDate, double carryOverRate) {
        String sourceKey = RankingRedisKey.daily(sourceDate);
        String targetKey = RankingRedisKey.daily(targetDate);
        String tempKey = RankingRedisKey.carryOverTemp(targetDate);
        Set<ZSetOperations.TypedTuple<String>> sourceEntries = masterRedisTemplate.opsForZSet()
            .rangeWithScores(sourceKey, 0, -1);

        masterRedisTemplate.delete(tempKey);
        if (sourceEntries == null || sourceEntries.isEmpty()) {
            return 0;
        }

        List<ZSetOperations.TypedTuple<String>> carriedEntries = sourceEntries.stream()
            .map(entry -> carryOverEntry(entry, carryOverRate))
            .toList();
        writeInBatches(tempKey, carriedEntries);

        long copiedCount = validateCopiedCount(tempKey, carriedEntries.size());
        applyExpiration(tempKey, targetDate);
        masterRedisTemplate.rename(tempKey, targetKey);
        return copiedCount;
    }

    private ZSetOperations.TypedTuple<String> carryOverEntry(
        ZSetOperations.TypedTuple<String> sourceEntry,
        double carryOverRate
    ) {
        return new DefaultTypedTuple<>(sourceEntry.getValue(), sourceEntry.getScore() * carryOverRate);
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

    private long validateCopiedCount(String tempKey, int expectedCount) {
        Long copiedCount = masterRedisTemplate.opsForZSet().zCard(tempKey);
        if (copiedCount == null || copiedCount != expectedCount) {
            throw new IllegalStateException("Ranking carry-over member count does not match source");
        }
        return copiedCount;
    }

    private void applyExpiration(String tempKey, LocalDate targetDate) {
        Boolean expirationApplied = masterRedisTemplate.expireAt(
            tempKey,
            Date.from(RankingExpirationPolicy.expiresAt(targetDate))
        );
        if (!Boolean.TRUE.equals(expirationApplied)) {
            throw new IllegalStateException("Failed to apply Ranking carry-over expiration");
        }
    }
}
