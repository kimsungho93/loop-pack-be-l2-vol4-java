package com.loopers.ranking.application;

import com.loopers.shared.pagination.PageQuery;
import com.loopers.shared.pagination.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

@RequiredArgsConstructor
@Service
public class RankingService {

    private final RankingQuery rankingQuery;

    public PageResult<RankingPosition> getDailyRanking(LocalDate date, PageQuery pageQuery) {
        long start = (long) pageQuery.page() * pageQuery.size();
        long end = start + pageQuery.size() - 1;
        RankingEntries entries = rankingQuery.findDaily(date, start, end);
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
