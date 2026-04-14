/**
 * BE 도메인과 1:1 매핑 (Spring record → TS type).
 * 참조: docs/02-design/features/mvp.design.md §3.2.
 */

export type TimeFrame = '1D' | '1W' | '1M' | '3M' | '1Y' | '5Y';

export interface SearchHit {
  ticker: string;
  name: string;
  exchange: string;
  matchType: string;
}

export interface StockProfile {
  ticker: string;
  name: string;
  exchange: string;
  currency: string;
  logoUrl: string | null;
  industry: string | null;
  marketCap: number | null;
}

export interface Quote {
  ticker: string;
  price: number;
  change: number;
  changePercent: number;
  high: number;
  low: number;
  open: number;
  previousClose: number;
  volume: number;
  updatedAt: string; // ISO 8601
}

export interface Candle {
  time: number; // epoch seconds (UTC)
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
}

export interface IndicatorSnapshot {
  ticker: string;
  rsi14: number;
  macd: {
    macd: number;
    signal: number;
    histogram: number;
  };
  bollinger: {
    upper: number;
    middle: number;
    lower: number;
    percentB: number;
  };
  movingAverage: {
    ma5: number;
    ma20: number;
    ma60: number;
  };
  tooltipsKo: Record<string, string>;
}

export interface ApiErrorBody {
  error: {
    code: string;
    message: string;
    details?: unknown;
    requestId: string;
    timestamp: string;
  };
}
