-- V1__init.sql
-- AI Stock Advisor MVP 초기 스키마
-- 참조: docs/02-design/features/mvp.design.md §3.3
--
-- 설계 원칙:
--   - DB는 MVP에 최소. 시세/지표/뉴스는 외부 API + Redis 캐시로 처리.
--   - PostgreSQL 17 + UTC 타임존 가정 (application.yml: hibernate.jdbc.time_zone=UTC).
--   - 유저/북마크 테이블은 Phase 4(Auth)에서 추가.

-- ============================================================
-- 1. popular_tickers — 인기 종목 마스터 (SEO SSR 랜딩용)
-- ============================================================
CREATE TABLE popular_tickers (
    ticker          VARCHAR(10) PRIMARY KEY,
    name            VARCHAR(200) NOT NULL,
    display_order   SMALLINT     NOT NULL,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  popular_tickers                 IS 'SSR/ISR 대상 인기 종목 마스터 (Phase 1~3, MVP).';
COMMENT ON COLUMN popular_tickers.ticker          IS '거래소 심볼 (예: AAPL).';
COMMENT ON COLUMN popular_tickers.name            IS '회사명 (예: Apple Inc.).';
COMMENT ON COLUMN popular_tickers.display_order   IS '랜딩 노출 순서 (오름차순).';
COMMENT ON COLUMN popular_tickers.is_active       IS '비활성화 시 랜딩에서 제외 (deprecated 종목 대응).';

CREATE INDEX idx_popular_tickers_active_order
    ON popular_tickers (is_active, display_order)
    WHERE is_active = TRUE;

-- ============================================================
-- 2. ai_signal_history — AI 시그널 이력 (감사·재현성·품질 분석)
-- ============================================================
-- PII 없음. context_hash는 (quote+indicators+news) 입력의 SHA-256 해시.
-- model_version/prompt_version으로 모델/프롬프트 변경 시 재현 가능.
CREATE TABLE ai_signal_history (
    id              BIGSERIAL    PRIMARY KEY,
    ticker          VARCHAR(10)  NOT NULL,
    signal          VARCHAR(10)  NOT NULL CHECK (signal     IN ('bullish', 'neutral', 'bearish')),
    confidence      VARCHAR(10)  NOT NULL CHECK (confidence IN ('low', 'mid', 'high')),
    summary_ko      TEXT         NOT NULL,
    rationale       JSONB        NOT NULL,
    risks           JSONB        NOT NULL,
    context_hash    CHAR(64)     NOT NULL,
    model_version   VARCHAR(50)  NOT NULL,
    prompt_version  VARCHAR(20)  NOT NULL,
    latency_ms      INTEGER      NOT NULL,
    cache_hit       BOOLEAN      NOT NULL DEFAULT FALSE,
    generated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ai_signal_history                 IS 'Gemini RAG 응답 감사 로그. PII 없음, 품질·비용 분석용.';
COMMENT ON COLUMN ai_signal_history.context_hash    IS '입력(quote+indicators+news) SHA-256. 동일 입력 재현·캐시 키 검증.';
COMMENT ON COLUMN ai_signal_history.rationale       IS 'JSON 배열: [{type, evidence}, ...].';
COMMENT ON COLUMN ai_signal_history.risks           IS 'JSON 배열: [string, ...].';
COMMENT ON COLUMN ai_signal_history.model_version   IS '예: gemini-1.5-flash-002.';
COMMENT ON COLUMN ai_signal_history.prompt_version  IS '예: v1, v2 (PromptBuilder 버전).';
COMMENT ON COLUMN ai_signal_history.latency_ms      IS 'LLM 호출 + 검증 총 지연 (캐시 히트 시 0).';
COMMENT ON COLUMN ai_signal_history.cache_hit       IS 'Redis ai:{ticker}:v{prompt} 적중 여부.';

CREATE INDEX idx_ai_signal_history_ticker_time ON ai_signal_history (ticker, generated_at DESC);
CREATE INDEX idx_ai_signal_history_hash        ON ai_signal_history (context_hash);

-- ============================================================
-- 3. legal_disclaimer_audit — 면책 고지 버전 감사
-- ============================================================
-- 법적 고지 문구 변경 시 감사 추적. content_hash로 무결성 검증.
CREATE TABLE legal_disclaimer_audit (
    id              BIGSERIAL    PRIMARY KEY,
    page            VARCHAR(100) NOT NULL,
    version         VARCHAR(20)  NOT NULL,
    content_hash    CHAR(64)     NOT NULL,
    changed_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  legal_disclaimer_audit              IS '면책/고지 변경 이력. docs/planning/07-legal-compliance.md 정책 추적.';
COMMENT ON COLUMN legal_disclaimer_audit.page         IS '예: "/", "/stock/[ticker]", "footer".';
COMMENT ON COLUMN legal_disclaimer_audit.version      IS '시맨틱 버전 (예: 1.0.0).';
COMMENT ON COLUMN legal_disclaimer_audit.content_hash IS '문구 SHA-256. 동일 문구 재기록 방지.';

CREATE INDEX idx_legal_disclaimer_audit_page ON legal_disclaimer_audit (page, changed_at DESC);
