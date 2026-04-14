'use client';

import { useQuery } from '@tanstack/react-query';

import { getProfile } from '@/lib/api/stocks';

import type { StockProfile } from '@/types/stock';

export function useProfile(ticker: string) {
  return useQuery<StockProfile>({
    queryKey: ['profile', ticker],
    queryFn: () => getProfile(ticker),
    staleTime: 24 * 60 * 60 * 1000,
  });
}
