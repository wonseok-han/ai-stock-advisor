'use client';

import { useQuery } from '@tanstack/react-query';

import { getMarketNews } from '@/lib/api/market';

import type { MarketNewsItem } from '@/types/market';

export function useMarketNews() {
  return useQuery<MarketNewsItem[]>({
    queryKey: ['market', 'news'],
    queryFn: () => getMarketNews(),
    staleTime: 15 * 60_000,
    refetchInterval: 15 * 60_000,
    retry: 1,
  });
}
