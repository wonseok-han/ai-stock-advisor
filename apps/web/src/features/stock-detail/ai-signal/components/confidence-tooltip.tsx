'use client';

import { useState } from 'react';

export function ConfidenceTooltip() {
  const [open, setOpen] = useState(false);

  return (
    <div className="relative inline-block">
      <button
        type="button"
        aria-expanded={open}
        onMouseEnter={() => setOpen(true)}
        onMouseLeave={() => setOpen(false)}
        onFocus={() => setOpen(true)}
        onBlur={() => setOpen(false)}
        onClick={() => setOpen((v) => !v)}
        className="ml-1 inline-flex h-4 w-4 shrink-0 cursor-help items-center justify-center rounded-full border border-border text-[10px] font-bold text-fg-muted hover:bg-bg-muted hover:text-fg focus:outline-none focus-visible:ring-2 focus-visible:ring-primary"
      >
        ?
      </button>
      {open && (
        <div
          role="tooltip"
          className="absolute right-0 top-full z-10 mt-1 w-56 rounded-md border border-border bg-bg-surface p-2.5 shadow-lg"
        >
          <p className="text-[11px] leading-relaxed text-fg-secondary">
            AI가 자체 분석에 대해 느끼는 확신 수준입니다. 주가 예측 확률이 아닙니다.
          </p>
        </div>
      )}
    </div>
  );
}
