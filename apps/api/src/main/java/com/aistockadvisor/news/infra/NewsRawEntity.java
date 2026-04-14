package com.aistockadvisor.news.infra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

import com.aistockadvisor.news.domain.NewsItem.Sentiment;

/**
 * news_raw 테이블 매핑 (Flyway V3).
 * 참조: docs/02-design/features/phase2-rag-pipeline.design.md §3.2
 */
@Entity
@Table(name = "news_raw")
public class NewsRawEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "ticker", nullable = false, length = 10)
    private String ticker;

    @Column(name = "article_url_hash", nullable = false, length = 64, unique = true)
    private String articleUrlHash;

    @Column(name = "source", nullable = false, length = 32)
    private String source;

    @Column(name = "source_url", nullable = false, columnDefinition = "TEXT")
    private String sourceUrl;

    @Column(name = "title_en", nullable = false, columnDefinition = "TEXT")
    private String titleEn;

    @Column(name = "title_ko", columnDefinition = "TEXT")
    private String titleKo;

    @Column(name = "summary_ko", columnDefinition = "TEXT")
    private String summaryKo;

    @Enumerated(EnumType.STRING)
    @Column(name = "sentiment", length = 16)
    private Sentiment sentiment;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    @Column(name = "translated_at")
    private Instant translatedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected NewsRawEntity() {
    }

    public NewsRawEntity(UUID id, String ticker, String articleUrlHash, String source,
                         String sourceUrl, String titleEn, Instant publishedAt, Instant createdAt) {
        this.id = id;
        this.ticker = ticker;
        this.articleUrlHash = articleUrlHash;
        this.source = source;
        this.sourceUrl = sourceUrl;
        this.titleEn = titleEn;
        this.publishedAt = publishedAt;
        this.createdAt = createdAt;
    }

    public void applyTranslation(String titleKo, String summaryKo, Sentiment sentiment, Instant translatedAt) {
        this.titleKo = titleKo;
        this.summaryKo = summaryKo;
        this.sentiment = sentiment;
        this.translatedAt = translatedAt;
    }

    public UUID getId() { return id; }
    public String getTicker() { return ticker; }
    public String getArticleUrlHash() { return articleUrlHash; }
    public String getSource() { return source; }
    public String getSourceUrl() { return sourceUrl; }
    public String getTitleEn() { return titleEn; }
    public String getTitleKo() { return titleKo; }
    public String getSummaryKo() { return summaryKo; }
    public Sentiment getSentiment() { return sentiment; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getTranslatedAt() { return translatedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
