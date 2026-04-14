'use client';

import { useQuery } from '@tanstack/react-query';

import { getIndicators } from '@/lib/api/stocks';

import type { IndicatorSnapshot } from '@/types/stock';

export function useIndicators(ticker: string) {
  return useQuery<IndicatorSnapshot>({
    queryKey: ['indicators', ticker],
    queryFn: () => getIndicators(ticker),
    staleTime: 5 * 60_000,
  });
}
