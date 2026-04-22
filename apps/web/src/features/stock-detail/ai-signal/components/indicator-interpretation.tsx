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
            className="rounded-sm border border-zinc-100 bg-white px-2.5 py-2 text-xs dark:border-zinc-800 dark:bg-zinc-900"
          >
            <div className="flex items-center justify-between gap-2">
              <span className="font-semibold text-zinc-700 dark:text-zinc-200">
                {it.indicator}
              </span>
              {it.value ? (
                <span className="text-[11px] text-zinc-500 dark:text-zinc-400">
                  {it.value}
                </span>
              ) : null}
            </div>
            <p className="mt-1 text-zinc-700 dark:text-zinc-300">
              {it.meaningKo}
            </p>
          </li>
        ))}
      </ul>
    </CollapsibleSection>
  );
}
