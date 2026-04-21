'use client';

import { useState } from 'react';

import { cn } from '@/lib/cn';

/**
 * AI 분석 섹션 공용 접이식 래퍼.
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
  return (
    <section className="rounded-md border border-zinc-100 bg-zinc-50 dark:border-zinc-800 dark:bg-zinc-950">
      <button
        type="button"
        onClick={() => setOpen((prev) => !prev)}
        aria-expanded={open}
        className="flex w-full items-center justify-between gap-2 rounded-md px-3 py-2 text-left text-xs font-semibold text-zinc-600 hover:bg-zinc-100 focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 dark:text-zinc-300 dark:hover:bg-zinc-900"
      >
        <span>{title}</span>
        <span
          aria-hidden="true"
          className={cn(
            'inline-block h-2 w-2 border-b border-r border-zinc-500 transition-transform dark:border-zinc-400',
            open ? 'rotate-45' : '-rotate-45',
          )}
        />
      </button>
      {open ? <div className="border-t border-zinc-100 p-3 dark:border-zinc-800">{children}</div> : null}
    </section>
  );
}
