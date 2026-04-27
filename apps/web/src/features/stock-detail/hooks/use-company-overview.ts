'use client';

import { useQuery } from '@tanstack/react-query';

import { getCompanyOverview } from '@/lib/api/stocks';

import type { CompanyOverview } from '@/types/stock';

export function useCompanyOverview(ticker: string) {
  return useQuery<CompanyOverview>({
    queryKey: ['overview', ticker],
    queryFn: () => getCompanyOverview(ticker),
    staleTime: 24 * 60 * 60 * 1000,
    retry: 1,
  });
}
