import { apiFetch } from '@/lib/api/client';

import type { MarketMovers, MarketNewsItem, MarketOverview } from '@/types/market';

export function getMarketOverview(): Promise<MarketOverview> {
  return apiFetch<MarketOverview>('/market/overview');
}

export function getMarketNews(limit = 10): Promise<MarketNewsItem[]> {
  return apiFetch<MarketNewsItem[]>(`/market/news?limit=${limit}`);
}

export function getMarketMovers(): Promise<MarketMovers> {
  return apiFetch<MarketMovers>('/market/movers');
}
