package com.aistockadvisor.stock.domain;

import java.math.BigDecimal;

/**
 * OHLCV 봉. time 은 unix epoch seconds (UTC).
 * 참조: docs/02-design/features/mvp.design.md §3.2.
 */
public record Candle(
        long time,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume
) {
}
