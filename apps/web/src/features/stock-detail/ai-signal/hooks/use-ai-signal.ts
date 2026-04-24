'use client';

import { useQuery } from '@tanstack/react-query';

import { getAiSignal } from '@/lib/api/ai-signal';

import type { AiSignal } from '@/types/ai-signal';

export function useAiSignal(ticker: string) {
  return useQuery<AiSignal>({
    queryKey: ['ai-signal', ticker],
    queryFn: () => getAiSignal(ticker),
    staleTime: 10 * 60 * 1000,
  });
}
