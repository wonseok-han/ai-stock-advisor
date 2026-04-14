'use client';

import { cn } from '@/lib/cn';

import type { TimeFrame } from '@/types/stock';

const FRAMES: { value: TimeFrame; label: string }[] = [
  { value: '1D', label: '1일' },
  { value: '1W', label: '1주' },
  { value: '1M', label: '1개월' },
  { value: '3M', label: '3개월' },
  { value: '1Y', label: '1년' },
  { value: '5Y', label: '5년' },
];

interface Props {
  value: TimeFrame;
  onChange: (tf: TimeFrame) => void;
  className?: string;
}

export function TimeFrameTabs({ value, onChange, className }: Props) {
  return (
    <div
      role="tablist"
      aria-label="기간 선택"
      className={cn(
        'inline-flex gap-1 rounded-lg border border-zinc-200 bg-white p-1 text-sm dark:border-zinc-800 dark:bg-zinc-900',
        className,
      )}
    >
      {FRAMES.map((f) => {
        const active = f.value === value;
        return (
          <button
            key={f.value}
            type="button"
            role="tab"
            aria-selected={active}
            onClick={() => onChange(f.value)}
            className={cn(
              'rounded-md px-3 py-1 transition-colors',
              active
                ? 'bg-zinc-900 text-white dark:bg-zinc-100 dark:text-zinc-900'
                : 'text-zinc-600 hover:bg-zinc-100 dark:text-zinc-400 dark:hover:bg-zinc-800',
            )}
          >
            {f.label}
          </button>
        );
      })}
    </div>
  );
}
