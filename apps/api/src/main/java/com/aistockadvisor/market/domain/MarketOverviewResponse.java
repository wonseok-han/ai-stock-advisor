package com.aistockadvisor.market.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * /market/overview 조합 응답.
 * 참조: docs/02-design/features/market-dashboard.design.md §4.1
 */
public record MarketOverviewResponse(
        List<MarketIndex> indices,
        BigDecimal usdKrw,
        BigDecimal usdKrwChange,
        OffsetDateTime updatedAt,
        String disclaimer
) {
}
