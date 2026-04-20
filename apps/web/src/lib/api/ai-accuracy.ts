import { apiFetch } from '@/lib/api/client';

import type { AiAccuracySummary, AiAccuracyWindow } from '@/types/ai-accuracy';

/**
 * 전역 AI 시그널 정합도 조회.
 *
 * 참조: docs/02-design/features/signal-accuracy.design.md §4.2
 *
 * 종목별이 아닌 전역 집계 — 유사투자자문 해석 여지를 차단하기 위한 의도적 설계.
 * 에러/샘플 부족 시 FE 에서 배지 숨김 처리.
 */
export function getAiAccuracy(
  window: AiAccuracyWindow = 30,
): Promise<AiAccuracySummary> {
  return apiFetch<AiAccuracySummary>(`/ai/accuracy?window=${window}`);
}
