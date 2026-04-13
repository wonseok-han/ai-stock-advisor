package com.aistockadvisor.stock.domain;

import java.math.BigDecimal;

/**
 * 종목 마스터 정보. 거의 변하지 않으므로 캐시 TTL 24h.
 * 참조: docs/02-design/features/mvp.design.md §3.2.
 */
public record StockProfile(
        String ticker,
        String name,
        String exchange,
        String currency,
        String logoUrl,
        String industry,
        BigDecimal marketCap
) {
}
