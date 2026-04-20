'use client';

import { useQuery } from '@tanstack/react-query';

import { getAiAccuracy } from '@/lib/api/ai-accuracy';

import type { AiAccuracySummary, AiAccuracyWindow } from '@/types/ai-accuracy';

/**
 * AI 시그널 정합도 조회 훅.
 *
 * 참조: docs/02-design/features/signal-accuracy.design.md §11.2 Step 6
 *
 * - BE 가 Redis 1h 캐싱하므로 FE staleTime 도 1h 로 맞춰 중복 호출 최소화.
 * - 에러 시 retry 1회 — 실패 확정 시 배지 자체를 숨기는 silent fallback 방식.
 */
export function useAccuracy(window: AiAccuracyWindow = 30) {
  return useQuery<AiAccuracySummary>({
    queryKey: ['ai-accuracy', window],
    queryFn: () => getAiAccuracy(window),
    staleTime: 60 * 60 * 1000,
    retry: 1,
  });
}
