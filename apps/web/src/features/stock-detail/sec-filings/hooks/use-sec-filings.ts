'use client';

import { useQuery } from '@tanstack/react-query';

import { getSecFilings } from '@/lib/api/stocks';

import type { SecFiling } from '@/types/stock';

export function useSecFilings(ticker: string) {
  return useQuery<SecFiling[]>({
    queryKey: ['sec-filings', ticker],
    queryFn: () => getSecFilings(ticker),
    staleTime: 60 * 60 * 1000,
    retry: 1,
  });
}
