package com.loopers.ranking.application;

import com.loopers.shared.pagination.PageQuery;
import com.loopers.shared.pagination.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

@RequiredArgsConstructor
@Service
public class RankingReadService {

    private final DailyRankingQuery dailyRankingQuery;

    public PageResult<RankingPosition> getDailyRanking(LocalDate date, PageQuery pageQuery) {
        long start = (long) pageQuery.page() * pageQuery.size();
        long end = start + pageQuery.size() - 1;
        DailyRankingEntries entries = dailyRankingQuery.findDaily(date, start, end);
        int totalPages = totalPages(entries.totalElements(), pageQuery.size());

        return new PageResult<>(
            positions(entries.productIds(), start),
            entries.totalElements(),
            totalPages,
            pageQuery.page(),
            pageQuery.size(),
            pageQuery.page() == 0,
            totalPages == 0 || pageQuery.page() >= totalPages - 1
        );
    }

    public Optional<Long> getDailyRank(LocalDate date, Long productId) {
        return dailyRankingQuery.findDailyRank(date, productId)
            .map(position -> position + 1);
    }

    private List<RankingPosition> positions(List<Long> productIds, long start) {
        return IntStream.range(0, productIds.size())
            .mapToObj(index -> new RankingPosition(start + index + 1, productIds.get(index)))
            .toList();
    }

    private int totalPages(long totalElements, int size) {
        if (totalElements == 0) {
            return 0;
        }
        return Math.toIntExact(((totalElements - 1) / size) + 1);
    }
}
