'use client';

import { useQuery } from '@tanstack/react-query';

import { getMarketRegimeAi } from '@/lib/api/market';

import type { MarketRegimeAi } from '@/types/market';

/** AI 국면 해석 — 로그인 사용자 전용. enabled=false면 호출하지 않음. */
export function useMarketRegimeAi(enabled: boolean) {
  return useQuery<MarketRegimeAi>({
    queryKey: ['market', 'regime', 'ai'],
    queryFn: () => getMarketRegimeAi(),
    enabled,
    staleTime: 30 * 60_000,
    retry: 1,
  });
}
