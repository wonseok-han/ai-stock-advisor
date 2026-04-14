'use client';

import { useQuery } from '@tanstack/react-query';

import { getQuote } from '@/lib/api/stocks';

import type { Quote } from '@/types/stock';

export function useQuote(ticker: string) {
  return useQuery<Quote>({
    queryKey: ['quote', ticker],
    queryFn: () => getQuote(ticker),
    staleTime: 30_000,
    refetchInterval: 30_000,
  });
}
