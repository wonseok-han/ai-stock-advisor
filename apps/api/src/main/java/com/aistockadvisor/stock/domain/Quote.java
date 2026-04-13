package com.aistockadvisor.stock.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 단일 종목 시세 스냅샷. 캐시 TTL 30s.
 * 참조: docs/02-design/features/mvp.design.md §3.2.
 */
public record Quote(
        String ticker,
        BigDecimal price,
        BigDecimal change,
        BigDecimal changePercent,
        BigDecimal high,
        BigDecimal low,
        BigDecimal open,
        BigDecimal previousClose,
        long volume,
        OffsetDateTime updatedAt
) {
}
