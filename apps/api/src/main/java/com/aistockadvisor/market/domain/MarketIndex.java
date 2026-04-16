package com.aistockadvisor.market.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 개별 지수/VIX 시세 DTO.
 * 참조: docs/02-design/features/market-dashboard.design.md §3.1
 */
public record MarketIndex(
        String symbol,
        String name,
        BigDecimal price,
        BigDecimal change,
        BigDecimal changePercent,
        OffsetDateTime updatedAt
) {
}
