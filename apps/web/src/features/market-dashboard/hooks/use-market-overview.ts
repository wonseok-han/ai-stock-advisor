'use client';

import { useQuery } from '@tanstack/react-query';

import { getMarketOverview } from '@/lib/api/market';

import type { MarketOverview } from '@/types/market';

export function useMarketOverview() {
  return useQuery<MarketOverview>({
    queryKey: ['market', 'overview'],
    queryFn: () => getMarketOverview(),
    staleTime: 5 * 60_000,
    refetchInterval: 5 * 60_000,
    retry: 1,
  });
}
