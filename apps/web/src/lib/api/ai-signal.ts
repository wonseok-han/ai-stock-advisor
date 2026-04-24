import { apiFetch } from '@/lib/api/client';

import type { AiSignal } from '@/types/ai-signal';

export function getAiSignal(ticker: string): Promise<AiSignal> {
  const t = encodeURIComponent(ticker);
  return apiFetch<AiSignal>(`/stocks/${t}/ai-signal`);
}
