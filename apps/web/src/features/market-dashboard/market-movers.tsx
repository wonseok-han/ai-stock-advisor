'use client';

import { useRouter } from 'next/navigation';

import { useMarketMovers } from '@/features/market-dashboard/hooks/use-market-movers';
import { cn } from '@/lib/cn';
import { formatUsd } from '@/lib/format/currency';
import { formatPercentChange } from '@/lib/format/percent';

import type { MarketMover } from '@/types/market';

export function MarketMovers() {
  const { data, isLoading, error, refetch } = useMarketMovers();

  if (isLoading) {
    return (
      <section aria-label="급등/급락 종목" className="card p-5">
        <div className="h-4 w-32 animate-pulse rounded bg-bg-skeleton" />
        <div className="mt-4 space-y-2">
          {Array.from({ length: 5 }).map((_, i) => (
            <div key={i} className="h-8 animate-pulse rounded bg-bg-skeleton" />
          ))}
        </div>
      </section>
    );
  }

  if (error || !data) {
    return (
      <section className="card p-5">
        <p className="text-sm text-danger">급등/급락 데이터를 불러올 수 없습니다.</p>
        <button
          onClick={() => refetch()}
          className="mt-2 cursor-pointer text-xs text-primary hover:underline"
        >
          다시 시도
        </button>
      </section>
    );
  }

  return (
    <section aria-label="급등/급락 종목" className="card overflow-hidden">
      <div className="border-b border-border px-5 py-4">
        <h2 className="text-sm font-semibold text-fg">급등/급락 종목</h2>
      </div>
      <div className="divide-y divide-border">
        <MoverList title="급등" movers={data.gainers} variant="gain" />
        <MoverList title="급락" movers={data.losers} variant="loss" />
      </div>
      <div className="border-t border-border px-5 py-2.5">
        <p className="text-[11px] text-fg-muted">
          인기 종목 {data.poolSize}개 기준 · {data.disclaimer}
        </p>
      </div>
    </section>
  );
}

function MoverList({
  title,
  movers,
  variant,
}: {
  title: string;
  movers: MarketMover[];
  variant: 'gain' | 'loss';
}) {
  const router = useRouter();
  const isGain = variant === 'gain';

  if (movers.length === 0) {
    return (
      <div className="px-5 py-4">
        <h3 className={cn('mb-2 text-xs font-medium', isGain ? 'text-success' : 'text-danger')}>
          {title}
        </h3>
        <p className="text-xs text-fg-muted">데이터 없음</p>
      </div>
    );
  }

  return (
    <div className="px-5 py-4">
      <h3 className={cn('mb-3 flex items-center gap-1.5 text-xs font-semibold', isGain ? 'text-success' : 'text-danger')}>
        <span className={cn('inline-block h-2 w-2 rounded-full', isGain ? 'bg-success' : 'bg-danger')} />
        {title}
      </h3>
      <ul className="space-y-0.5">
        {movers.map((m, i) => (
          <li key={m.ticker}>
            <button
              onClick={() => router.push(`/stock/${m.ticker}`)}
              className={cn(
                'flex w-full cursor-pointer items-center justify-between gap-3 rounded-lg px-2.5 py-2 text-left transition-colors hover:bg-bg-muted',
                i === 0 && 'bg-bg-muted/50',
              )}
            >
              <div className="min-w-0 flex-1">
                <div className="flex items-baseline gap-1.5">
                  <span className="shrink-0 text-sm font-semibold text-fg">{m.ticker}</span>
                  <span className="truncate text-xs text-fg-muted">{m.name}</span>
                </div>
              </div>
              <div className="flex shrink-0 items-baseline gap-2 text-right">
                <span className="text-xs tabular-nums text-fg-secondary">{formatUsd(m.price)}</span>
                <span
                  className={cn(
                    'min-w-[4rem] rounded-md px-1.5 py-0.5 text-right text-xs tabular-nums font-semibold',
                    isGain ? 'bg-emerald-500/10 text-success' : 'bg-red-500/10 text-danger',
                  )}
                >
                  {formatPercentChange(m.changePercent)}
                </span>
              </div>
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}
