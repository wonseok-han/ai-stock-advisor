import { apiFetch } from '@/lib/api/client';

import type {
  MarketNewsItem,
  MarketOverview,
  MarketRegime,
  MarketRegimeAi,
  SectorPerformance,
} from '@/types/market';

export function getMarketOverview(): Promise<MarketOverview> {
  return apiFetch<MarketOverview>('/market/overview');
}

export function getMarketNews(limit = 10, before?: number): Promise<MarketNewsItem[]> {
  const params = new URLSearchParams({ limit: String(limit) });
  if (before != null) params.set('before', String(before));
  return apiFetch<MarketNewsItem[]>(`/market/news?${params.toString()}`);
}

export function getSectorPerformance(): Promise<SectorPerformance[]> {
  return apiFetch<SectorPerformance[]>('/market/sectors');
}

export function getMarketRegime(): Promise<MarketRegime> {
  return apiFetch<MarketRegime>('/market/regime');
}

export function getMarketRegimeAi(): Promise<MarketRegimeAi> {
  return apiFetch<MarketRegimeAi>('/market/regime/ai');
}
