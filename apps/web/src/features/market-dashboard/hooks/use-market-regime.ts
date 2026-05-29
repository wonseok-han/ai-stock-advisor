'use client';

import { useQuery } from '@tanstack/react-query';

import { getMarketRegime } from '@/lib/api/market';

import type { MarketRegime } from '@/types/market';

export function useMarketRegime() {
  return useQuery<MarketRegime>({
    queryKey: ['market', 'regime'],
    queryFn: () => getMarketRegime(),
    staleTime: 30 * 60_000,
    retry: 1,
  });
}
