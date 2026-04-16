package com.aistockadvisor.market.domain;

/**
 * 시장 뉴스 아이템 DTO.
 * 참조: docs/02-design/features/market-dashboard.design.md §3.1
 */
public record MarketNewsItem(
        long id,
        String source,
        String sourceUrl,
        String titleEn,
        String titleKo,
        String summaryKo,
        long publishedAt,
        String disclaimer
) {
}
