/**
 * BE 도메인과 1:1 매핑 (Spring record → TS type).
 * 참조: docs/02-design/features/mvp.design.md §3.2.
 */

export type TimeFrame = '1D' | '1W' | '1M' | '3M' | '1Y' | '5Y';

export interface SearchHit {
  ticker: string;
  name: string;
  exchange: string | null;
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

export type MarketStatus = 'OPEN' | 'CLOSED';

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
  marketStatus: MarketStatus;
  priceLabel: string;
  week52High: number | null;
  week52Low: number | null;
}

export interface CompanyOverview {
  sector: string | null;
  industry: string | null;
  marketCap: number | null;
  peRatio: number | null;
  eps: number | null;
  dividendPerShare: number | null;
  beta: number | null;
  week52High: number | null;
  week52Low: number | null;
  description: string | null;
  employees: number | null;
  website: string | null;
  ipoDate: string | null;
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
    ma10: number | null;
    ma20: number;
    ma50: number | null;
    ma60: number;
    ma120: number | null;
    ma200: number | null;
  };
  tooltipsKo: Record<string, string>;
}

export interface StockDetail {
  profile: StockProfile | null;
  quote: Quote | null;
  overview: CompanyOverview | null;
  candles: Candle[] | null;
  indicators: IndicatorSnapshot | null;
  news: import('./news').NewsItem[] | null;
  aiSignal: import('./ai-signal').AiSignal | null;
  disclaimer: {
    page: string;
    version: string;
    text: string;
  };
  partial: boolean;
  errors: { block: string; code: string; message: string }[];
  meta: {
    requestId: string;
    timestamp: string;
  };
}

export interface AnalystEstimates {
  rating: {
    score: number;
    label: string;
    labelKo: string;
    totalAnalysts: number;
    distribution: {
      strongBuy: number;
      buy: number;
      hold: number;
      sell: number;
      strongSell: number;
    };
  } | null;
  priceTarget: {
    current: number;
    high: number;
    low: number;
    mean: number;
    median: number;
    upsidePercent: number;
  } | null;
  earnings: {
    quarter: string;
    epsActual: number;
    epsEstimate: number;
    surprisePercent: number;
    result: 'BEAT' | 'MISS' | 'MEET' | 'EST';
  }[];
}

export interface SecFiling {
  ticker: string;
  form: string;
  title: string;
  filedAt: string; // ISO date "2026-01-15"
  eventCategory: string;
  daysAgo: number;
  documentUrl: string | null;
  contentSummary: string | null;
  summaryKo: string | null;
  sentiment: '긍정' | '부정' | '중립' | null;
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
