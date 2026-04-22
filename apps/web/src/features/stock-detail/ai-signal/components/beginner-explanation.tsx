'use client';

import { CollapsibleSection } from '@/features/stock-detail/ai-signal/components/collapsible-section';

/**
 * AI 참고 분석 — 초보자 해설.
 * 비어있으면 섹션 자체를 숨깁니다 (graceful degrade).
 * 참조: docs/02-design/features/ai-analysis-deepening.design.md §5
 */
export function BeginnerExplanation({ text }: { text?: string | null }) {
  if (!text || text.trim().length === 0) return null;
  return (
    <CollapsibleSection title="쉽게 이해하기">
      <p className="text-sm leading-relaxed text-fg-secondary">{text}</p>
    </CollapsibleSection>
  );
}
