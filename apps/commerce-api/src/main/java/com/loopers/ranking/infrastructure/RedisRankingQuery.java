package com.loopers.ranking.infrastructure;

import com.loopers.ranking.RankingRedisKey;
import com.loopers.ranking.application.RankingEntries;
import com.loopers.ranking.application.RankingQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@RequiredArgsConstructor
@Component
public class RedisRankingQuery implements RankingQuery {

    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public RankingEntries findDaily(LocalDate date, long start, long end) {
        String key = RankingRedisKey.daily(date);
        Set<String> members = redisTemplate.opsForZSet().reverseRange(key, start, end);
        Long totalElements = redisTemplate.opsForZSet().zCard(key);

        return new RankingEntries(toProductIds(members), totalElements == null ? 0 : totalElements);
    }

    private List<Long> toProductIds(Set<String> members) {
        if (members == null) {
            return List.of();
        }
        return members.stream()
            .map(Long::valueOf)
            .toList();
    }
}
