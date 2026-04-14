-- V3__phase2_news_raw.sql
-- Phase 2: 뉴스 원문 + LLM 번역 캐시 (24h TTL 은 애플리케이션 레이어에서 체크)
-- 참조: docs/02-design/features/phase2-rag-pipeline.design.md §3.2

CREATE TABLE news_raw (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticker             VARCHAR(10) NOT NULL,
    article_url_hash   VARCHAR(64) NOT NULL,
    source             VARCHAR(32) NOT NULL,
    source_url         TEXT NOT NULL,
    title_en           TEXT NOT NULL,
    title_ko           TEXT,
    summary_ko         TEXT,
    sentiment          VARCHAR(16),
    published_at       TIMESTAMPTZ NOT NULL,
    translated_at      TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_news_raw_url_hash UNIQUE (article_url_hash)
);

CREATE INDEX idx_news_raw_ticker_published
    ON news_raw (ticker, published_at DESC);

CREATE INDEX idx_news_raw_translated_at
    ON news_raw (translated_at)
    WHERE translated_at IS NOT NULL;
