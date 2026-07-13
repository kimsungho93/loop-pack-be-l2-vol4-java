package com.loopers.ranking.application;

import com.loopers.product.application.ProductListInfo;
import com.loopers.product.application.ProductListQuery;
import com.loopers.shared.pagination.PageQuery;
import com.loopers.shared.pagination.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class RankingFacade {

    private final RankingService rankingService;
    private final ProductListQuery productListQuery;

    public PageResult<RankingItemInfo> getDailyRankings(LocalDate date, int page, int size) {
        PageResult<RankingPosition> rankingPage = rankingService.getDailyRanking(date, new PageQuery(page, size));
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
