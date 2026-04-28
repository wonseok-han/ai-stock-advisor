'use client';

import Link from 'next/link';

import { AiSignalPanel } from '@/features/stock-detail/ai-signal/ai-signal-panel';
import { StockHeader } from '@/features/stock-detail/stock-header';

export function AiSignalPageView({ ticker }: { ticker: string }) {
  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center gap-2">
        <Link
          href={`/stock/${ticker}`}
          className="flex items-center gap-1 rounded-lg px-2 py-1 text-sm text-fg-muted transition-colors hover:bg-bg-muted hover:text-fg"
        >
          <svg
            aria-hidden="true"
            className="h-4 w-4"
            fill="none"
            viewBox="0 0 24 24"
            strokeWidth={2}
            stroke="currentColor"
          >
            <path strokeLinecap="round" strokeLinejoin="round" d="M15.75 19.5L8.25 12l7.5-7.5" />
          </svg>
          종목 상세
        </Link>
      </div>
      <StockHeader ticker={ticker} />
      <AiSignalPanel ticker={ticker} />
    </div>
  );
}
