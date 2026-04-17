-- V8__candles.sql
-- Phase 4.5: 일봉 OHLCV + adjusted close 저장 테이블.
-- Yahoo Finance 벌크 로드 + on-demand + 일간 배치로 적재.
-- 참조: docs/02-design/features/phase4.5-improvements.design.md §3.1

CREATE TABLE candles (
    ticker      VARCHAR(10)     NOT NULL,
    trade_date  DATE            NOT NULL,
    open        NUMERIC(12,4)   NOT NULL,
    high        NUMERIC(12,4)   NOT NULL,
    low         NUMERIC(12,4)   NOT NULL,
    close       NUMERIC(12,4)   NOT NULL,
    adj_close   NUMERIC(12,4)   NOT NULL,
    volume      BIGINT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    PRIMARY KEY (ticker, trade_date)
);

COMMENT ON TABLE  candles               IS '일봉 OHLCV + adjusted close. Yahoo Finance SoR.';
COMMENT ON COLUMN candles.ticker        IS '거래소 심볼 (예: AAPL).';
COMMENT ON COLUMN candles.trade_date    IS '거래일 (UTC 기준).';
COMMENT ON COLUMN candles.adj_close     IS '배당/분할 조정 종가. 차트·지표 계산 기본값.';

CREATE INDEX idx_candles_ticker_date ON candles (ticker, trade_date DESC);
