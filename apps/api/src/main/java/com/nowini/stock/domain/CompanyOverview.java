package com.nowini.stock.domain;

import java.math.BigDecimal;

/**
 * 기업 펀더멘털 개요. FMP Company Profile 기반, Redis 24h 캐시.
 */
public record CompanyOverview(
        String sector,
        String industry,
        BigDecimal marketCap,
        BigDecimal peRatio,
        BigDecimal eps,
        BigDecimal dividendPerShare,
        BigDecimal beta,
        BigDecimal week52High,
        BigDecimal week52Low,
        String description,
        Integer employees,
        String website,
        String ipoDate
) {
}
