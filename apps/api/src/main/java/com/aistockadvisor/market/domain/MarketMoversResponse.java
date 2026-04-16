package com.aistockadvisor.market.domain;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * /market/movers 응답.
 * 참조: docs/02-design/features/market-dashboard.design.md §4.3
 */
public record MarketMoversResponse(
        List<MarketMover> gainers,
        List<MarketMover> losers,
        int poolSize,
        OffsetDateTime updatedAt,
        String disclaimer
) {
}
