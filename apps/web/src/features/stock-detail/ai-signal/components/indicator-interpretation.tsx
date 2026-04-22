'use client';

import { CollapsibleSection } from '@/features/stock-detail/ai-signal/components/collapsible-section';

import type { IndicatorInterpretation as IndicatorItem } from '@/types/ai-signal';

/**
 * AI 참고 분석 — 기술 지표 해석 리스트.
 * 참조: docs/02-design/features/ai-analysis-deepening.design.md §5
 */
export function IndicatorInterpretation({
  items,
}: {
  items?: IndicatorItem[] | null;
}) {
  if (!items || items.length === 0) return null;
  return (
    <CollapsibleSection title="기술 지표가 말하는 것">
      <ul className="flex flex-col gap-2">
        {items.map((it, idx) => (
          <li
            key={`${it.indicator}-${idx}`}
            className="rounded-sm border border-border bg-bg-surface px-2.5 py-2 text-xs"
          >
            <div className="flex items-center justify-between gap-2">
              <span className="font-semibold text-fg-secondary">
                {it.indicator}
              </span>
              {it.value ? (
                <span className="text-[11px] text-fg-muted">
                  {it.value}
                </span>
              ) : null}
            </div>
            <p className="mt-1 text-fg-secondary">
              {it.meaningKo}
            </p>
          </li>
        ))}
      </ul>
    </CollapsibleSection>
  );
}
