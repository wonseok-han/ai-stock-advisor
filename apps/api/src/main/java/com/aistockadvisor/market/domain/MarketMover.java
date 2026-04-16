package com.aistockadvisor.market.domain;

import java.math.BigDecimal;

/**
 * 급등/급락 개별 종목 DTO.
 * 참조: docs/02-design/features/market-dashboard.design.md §3.1
 */
public record MarketMover(
        String ticker,
        String name,
        BigDecimal price,
        BigDecimal change,
        BigDecimal changePercent
) {
}
