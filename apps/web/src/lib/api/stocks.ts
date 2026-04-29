import { apiFetch } from '@/lib/api/client';

import type {
  AnalystEstimates,
  Candle,
  CompanyOverview,
  IndicatorSnapshot,
  Quote,
  SearchHit,
  SecFiling,
  StockDetail,
  StockProfile,
  TimeFrame,
} from '@/types/stock';

export function searchStocks(query: string, signal?: AbortSignal): Promise<SearchHit[]> {
  const q = encodeURIComponent(query);
  return apiFetch<SearchHit[]>(`/stocks/search?q=${q}`, { signal });
}

export function getProfile(ticker: string): Promise<StockProfile> {
  return apiFetch<StockProfile>(`/stocks/${ticker}/profile`);
}

export function getQuote(ticker: string): Promise<Quote> {
  return apiFetch<Quote>(`/stocks/${ticker}/quote`);
}

export function getCandles(ticker: string, tf: TimeFrame): Promise<Candle[]> {
  return apiFetch<Candle[]>(`/stocks/${ticker}/candles?tf=${tf}`);
}

export function getIndicators(ticker: string): Promise<IndicatorSnapshot> {
  return apiFetch<IndicatorSnapshot>(`/stocks/${ticker}/indicators`);
}

export function getCompanyOverview(ticker: string): Promise<CompanyOverview> {
  return apiFetch<CompanyOverview>(`/stocks/${ticker}/overview`);
}

export async function getAnalystEstimates(ticker: string): Promise<AnalystEstimates | null> {
  const data = await apiFetch<AnalystEstimates | null>(`/stocks/${ticker}/analyst`);
  return data ?? null;
}

export function getSecFilings(ticker: string): Promise<SecFiling[]> {
  return apiFetch<SecFiling[]>(`/stocks/${ticker}/sec-filings`);
}

export function getDetail(ticker: string, tf: TimeFrame): Promise<StockDetail> {
  return apiFetch<StockDetail>(`/stocks/${ticker}/detail?tf=${tf}`);
}
