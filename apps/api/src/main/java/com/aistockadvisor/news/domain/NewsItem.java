package com.aistockadvisor.news.domain;

import java.time.Instant;

/**
 * 뉴스 아이템 응답 DTO.
 * 참조: docs/02-design/features/phase2-rag-pipeline.design.md §3.1
 */
public record NewsItem(
        String ticker,
        String articleUrlHash,
        String source,
        String sourceUrl,
        String titleEn,
        String titleKo,
        String summaryKo,
        Sentiment sentiment,
        Instant publishedAt,
        Instant translatedAt,
        String disclaimer
) {
    public enum Sentiment { POSITIVE, NEUTRAL, NEGATIVE }
}
