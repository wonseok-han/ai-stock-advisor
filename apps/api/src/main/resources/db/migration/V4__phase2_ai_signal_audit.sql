-- V4__phase2_ai_signal_audit.sql
-- Phase 2: AI 시그널 감사 로그 (영구 보관, 법적 대비)
-- 참조: docs/02-design/features/phase2-rag-pipeline.design.md §3.2

CREATE TABLE ai_signal_audit (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticker             VARCHAR(10) NOT NULL,
    request_id         UUID NOT NULL,
    signal             VARCHAR(16) NOT NULL,
    confidence         NUMERIC(3,2) NOT NULL,
    timeframe          VARCHAR(8) NOT NULL,
    rationale          JSONB NOT NULL,
    risks              JSONB NOT NULL,
    summary_ko         TEXT NOT NULL,
    model_name         VARCHAR(64) NOT NULL,
    context_payload    JSONB NOT NULL,
    raw_response       JSONB,
    forbidden_detected JSONB,
    fallback           BOOLEAN NOT NULL DEFAULT FALSE,
    latency_ms         INT NOT NULL,
    tokens_in          INT,
    tokens_out         INT,
    generated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ai_signal_audit_ticker_generated
    ON ai_signal_audit (ticker, generated_at DESC);

CREATE INDEX idx_ai_signal_audit_forbidden
    ON ai_signal_audit USING GIN (forbidden_detected)
    WHERE forbidden_detected IS NOT NULL;
