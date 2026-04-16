-- V5__stock_symbol.sql
-- 미국 주식 심볼 마스터 (로컬 검색용, 외부 API 호출 제거).
-- Finnhub /stock/symbol?exchange=US 데이터를 주기적으로 동기화.

-- pg_trgm: 유사 문자열 검색 (Supabase 기본 제공).
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE stock_symbol (
    symbol          VARCHAR(20)  PRIMARY KEY,
    description     VARCHAR(500) NOT NULL,
    type            VARCHAR(50),
    currency        VARCHAR(10),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  stock_symbol              IS 'US 주식 심볼 마스터. Finnhub /stock/symbol 동기화.';
COMMENT ON COLUMN stock_symbol.symbol       IS '거래소 심볼 (예: AAPL).';
COMMENT ON COLUMN stock_symbol.description  IS '종목명 (예: Apple Inc).';
COMMENT ON COLUMN stock_symbol.type         IS '종목 유형 (예: Common Stock, ETP, ADR).';

CREATE INDEX idx_stock_symbol_description ON stock_symbol USING gin (description gin_trgm_ops);
CREATE INDEX idx_stock_symbol_upper ON stock_symbol (upper(symbol));
