package com.loopers.ranking.application;

import com.loopers.product.application.ProductListInfo;
import com.loopers.product.application.ProductListQuery;
import com.loopers.shared.error.CoreException;
import com.loopers.shared.error.ErrorType;
import com.loopers.shared.pagination.PageQuery;
import com.loopers.shared.pagination.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Component
public class RankingFacade {

    private final RankingService rankingService;
    private final ProductListQuery productListQuery;
    private final RankingMetrics rankingMetrics;

    public PageResult<RankingItemInfo> getDailyRankings(LocalDate date, int page, int size) {
        PageResult<RankingPosition> rankingPage = getRankingPage(date, new PageQuery(page, size));
        if (rankingPage.content().isEmpty()) {
            return withContent(rankingPage, List.of());
        }

        List<Long> productIds = rankingPage.content().stream()
            .map(RankingPosition::productId)
            .toList();
        Map<Long, ProductListInfo> productsById = productListQuery.findVisibleProductsByIds(productIds).stream()
            .collect(Collectors.toMap(ProductListInfo::id, Function.identity()));
        List<RankingItemInfo> content = rankingPage.content().stream()
            .filter(position -> productsById.containsKey(position.productId()))
            .map(position -> new RankingItemInfo(position.rank(), productsById.get(position.productId())))
            .toList();

        return withContent(rankingPage, content);
    }

    private PageResult<RankingPosition> getRankingPage(LocalDate date, PageQuery pageQuery) {
        try {
            return rankingService.getDailyRanking(date, pageQuery);
        } catch (DataAccessException e) {
            rankingMetrics.recordPageLookupFailure();
            log.error("Failed to look up daily ranking. date={}", date, e);
            throw new CoreException(
                ErrorType.SERVICE_UNAVAILABLE,
                "랭킹을 잠시 조회할 수 없습니다. 잠시 후 다시 시도해주세요."
            );
        }
    }

    private PageResult<RankingItemInfo> withContent(
        PageResult<RankingPosition> rankingPage,
        List<RankingItemInfo> content
    ) {
        return new PageResult<>(
            content,
            rankingPage.totalElements(),
            rankingPage.totalPages(),
            rankingPage.number(),
            rankingPage.size(),
            rankingPage.first(),
            rankingPage.last()
        );
    }
}
