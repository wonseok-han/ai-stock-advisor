'use client';

import { useQuery } from '@tanstack/react-query';

import { getMarketMovers } from '@/lib/api/market';

import type { MarketMovers } from '@/types/market';

export function useMarketMovers() {
  return useQuery<MarketMovers>({
    queryKey: ['market', 'movers'],
    queryFn: () => getMarketMovers(),
    staleTime: 15 * 60_000,
    refetchInterval: 15 * 60_000,
    retry: 1,
  });
}
