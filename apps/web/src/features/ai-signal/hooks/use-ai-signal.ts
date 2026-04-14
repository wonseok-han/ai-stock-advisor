'use client';

import { useQuery } from '@tanstack/react-query';

import { getAiSignal } from '@/lib/api/ai-signal';

import type { AiSignal } from '@/types/ai-signal';
import type { TimeFrame } from '@/types/stock';

export function useAiSignal(ticker: string, tf: TimeFrame) {
  return useQuery<AiSignal>({
    queryKey: ['ai-signal', ticker, tf],
    queryFn: () => getAiSignal(ticker, tf),
    staleTime: 10 * 60 * 1000,
  });
}
