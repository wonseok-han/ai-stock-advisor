package com.nowini.market.infra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * market_news 테이블 매핑 (Flyway V20).
 * 시장 일반 뉴스 영속화 — 배치 수집·번역 후 저장, 화면은 커서 페이지네이션으로 조회.
 */
@Entity
@Table(name = "market_news")
public class MarketNewsEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "article_url_hash", nullable = false, length = 64, unique = true)
    private String articleUrlHash;

    @Column(name = "source", nullable = false, length = 64)
    private String source;

    @Column(name = "source_url", nullable = false, columnDefinition = "TEXT")
    private String sourceUrl;

    @Column(name = "title_en", nullable = false, columnDefinition = "TEXT")
    private String titleEn;

    @Column(name = "title_ko", columnDefinition = "TEXT")
    private String titleKo;

    @Column(name = "summary_en", columnDefinition = "TEXT")
    private String summaryEn;

    @Column(name = "summary_ko", columnDefinition = "TEXT")
    private String summaryKo;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    @Column(name = "translated_at")
    private Instant translatedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MarketNewsEntity() {
    }

    /** 번역 결과 반영. 실패 시 titleKo/summaryKo=null 로 호출하되 translatedAt 은 채워 재시도 방지. */
    public void applyTranslation(String titleKo, String summaryKo, Instant translatedAt) {
        this.titleKo = titleKo;
        this.summaryKo = summaryKo;
        this.translatedAt = translatedAt;
    }

    public UUID getId() { return id; }
    public String getArticleUrlHash() { return articleUrlHash; }
    public String getSource() { return source; }
    public String getSourceUrl() { return sourceUrl; }
    public String getTitleEn() { return titleEn; }
    public String getTitleKo() { return titleKo; }
    public String getSummaryEn() { return summaryEn; }
    public String getSummaryKo() { return summaryKo; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getTranslatedAt() { return translatedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
