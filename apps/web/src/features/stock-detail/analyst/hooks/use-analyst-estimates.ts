'use client';

import { useQuery } from '@tanstack/react-query';

import { getAnalystEstimates } from '@/lib/api/stocks';

import type { AnalystEstimates } from '@/types/stock';

export function useAnalystEstimates(ticker: string) {
  return useQuery<AnalystEstimates | null>({
    queryKey: ['analyst', ticker],
    queryFn: async () => (await getAnalystEstimates(ticker)) ?? null,
    staleTime: 24 * 60 * 60 * 1000,
    retry: 1,
  });
}
