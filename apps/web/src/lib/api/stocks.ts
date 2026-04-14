import { apiFetch } from '@/lib/api/client';

import type {
  Candle,
  IndicatorSnapshot,
  Quote,
  SearchHit,
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
