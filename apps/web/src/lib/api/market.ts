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

export function getMarketNews(limit = 10): Promise<MarketNewsItem[]> {
  return apiFetch<MarketNewsItem[]>(`/market/news?limit=${limit}`);
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
