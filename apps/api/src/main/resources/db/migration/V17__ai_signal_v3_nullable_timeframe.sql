-- v3: AI 시그널에서 timeframe 분리 — 기존 레코드 보존, 신규는 null
ALTER TABLE ai_signal_audit ALTER COLUMN timeframe DROP NOT NULL;
