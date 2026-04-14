import { apiFetch } from '@/lib/api/client';

import type { AiSignal } from '@/types/ai-signal';
import type { TimeFrame } from '@/types/stock';

export function getAiSignal(ticker: string, tf: TimeFrame): Promise<AiSignal> {
  const t = encodeURIComponent(ticker);
  return apiFetch<AiSignal>(`/ai-signal?ticker=${t}&tf=${tf}`);
}
