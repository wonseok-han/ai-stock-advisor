'use client';

import { useId, useState } from 'react';

import { cn } from '@/lib/cn';

/**
 * AI 분석 섹션 공용 접이식 래퍼.
 * grid-template-rows 트릭으로 콘텐츠 높이 독립적인 부드러운 전개/수축.
 * motion-reduce 사용자는 애니메이션 비활성화.
 * 참조: docs/02-design/features/ai-analysis-deepening.design.md §5, §10.3
 */
export function CollapsibleSection({
  title,
  defaultOpen = true,
  children,
}: {
  title: string;
  defaultOpen?: boolean;
  children: React.ReactNode;
}) {
  const [open, setOpen] = useState(defaultOpen);
  const panelId = useId();
  return (
    <section className="rounded-xl bg-bg-muted">
      <button
        type="button"
        onClick={() => setOpen((prev) => !prev)}
        aria-expanded={open}
        aria-controls={panelId}
        className="flex w-full items-center justify-between gap-2 rounded-xl px-3 py-2 text-left text-xs font-semibold text-fg-secondary hover:bg-bg-muted focus:outline-none focus-visible:ring-2 focus-visible:ring-primary"
      >
        <span>{title}</span>
        <svg
          aria-hidden="true"
          className={cn(
            'h-3.5 w-3.5 shrink-0 text-fg-muted transition-transform duration-200 ease-out motion-reduce:transition-none',
            open ? 'rotate-180' : 'rotate-0',
          )}
          fill="none"
          viewBox="0 0 24 24"
          strokeWidth={2.5}
          stroke="currentColor"
        >
          <path strokeLinecap="round" strokeLinejoin="round" d="M19.5 8.25l-7.5 7.5-7.5-7.5" />
        </svg>
      </button>
      <div
        id={panelId}
        className={cn(
          'grid transition-[grid-template-rows] duration-200 ease-out motion-reduce:transition-none',
          open ? 'grid-rows-[1fr]' : 'grid-rows-[0fr]',
        )}
      >
        <div className={open ? 'overflow-visible' : 'overflow-hidden'}>
          <div
            className={cn(
              'border-t border-border p-3 transition-opacity duration-200 ease-out motion-reduce:transition-none',
              open ? 'opacity-100' : 'opacity-0',
            )}
            aria-hidden={!open}
          >
            {children}
          </div>
        </div>
      </div>
    </section>
  );
}
