'use client';

import { CollapsibleSection } from '@/features/stock-detail/ai-signal/components/collapsible-section';

/**
 * AI 참고 분석 — 관전 포인트 체크리스트.
 * 참조: docs/02-design/features/ai-analysis-deepening.design.md §5
 */
export function WhatToWatch({ items }: { items?: string[] | null }) {
  const clean = (items ?? []).filter((s) => s && s.trim().length > 0);
  if (clean.length === 0) return null;
  return (
    <CollapsibleSection title="앞으로 지켜볼 포인트">
      <ul className="flex flex-col gap-1.5">
        {clean.map((text, idx) => (
          <li
            key={`${idx}-${text.slice(0, 16)}`}
            className="flex items-start gap-2 text-xs text-zinc-700 dark:text-zinc-300"
          >
            <span
              className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-blue-400"
              aria-hidden="true"
            />
            <span>{text}</span>
          </li>
        ))}
      </ul>
    </CollapsibleSection>
  );
}
