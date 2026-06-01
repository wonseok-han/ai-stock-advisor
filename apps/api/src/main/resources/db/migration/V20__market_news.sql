-- V20__market_news.sql
-- 시장 일반 뉴스 영속화 — 스케줄러 배치가 Finnhub general news 를 수집·번역해 저장하고,
-- 화면은 이 테이블을 페이지네이션(커서)으로 조회한다(과거 데이터 열람 가능).
-- 번역 24h 캐시인 news_raw(종목별)와 달리 시장 뉴스는 영구 보관(히스토리 누적).

CREATE TABLE market_news (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    article_url_hash   VARCHAR(64) NOT NULL,
    source             VARCHAR(64) NOT NULL,
    source_url         TEXT NOT NULL,
    title_en           TEXT NOT NULL,
    title_ko           TEXT,
    summary_en         TEXT,
    summary_ko         TEXT,
    published_at       TIMESTAMPTZ NOT NULL,
    translated_at      TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_market_news_url_hash UNIQUE (article_url_hash)
);

-- 커서 페이지네이션(published_at < before ORDER BY published_at DESC)용
CREATE INDEX idx_market_news_published ON market_news (published_at DESC);

-- 번역 대기 항목 스캔용 (배치)
CREATE INDEX idx_market_news_untranslated
    ON market_news (published_at DESC)
    WHERE translated_at IS NULL;
