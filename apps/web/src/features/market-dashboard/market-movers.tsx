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
        <div className="h-4 w-32 animate-pulse rounded bg-bg-muted" />
        <div className="mt-4 space-y-2">
          {Array.from({ length: 5 }).map((_, i) => (
            <div key={i} className="h-8 animate-pulse rounded bg-bg-muted" />
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
    <section aria-label="급등/급락 종목" className="card p-5">
      <h2 className="text-sm font-semibold text-fg">급등/급락 종목</h2>
      <div className="mt-4 space-y-5">
        <MoverList title="급등" movers={data.gainers} variant="gain" />
        <MoverList title="급락" movers={data.losers} variant="loss" />
      </div>
      <p className="mt-4 text-[11px] text-fg-muted">
        인기 종목 {data.poolSize}개 기준 · {data.disclaimer}
      </p>
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
      <div>
        <h3 className={cn('mb-2 text-xs font-medium', isGain ? 'text-success' : 'text-danger')}>
          {title}
        </h3>
        <p className="text-xs text-fg-muted">데이터 없음</p>
      </div>
    );
  }

  return (
    <div>
      <h3 className={cn('mb-2 text-xs font-medium', isGain ? 'text-success' : 'text-danger')}>
        {title}
      </h3>
      <ul className="space-y-0.5">
        {movers.map((m) => (
          <li key={m.ticker}>
            <button
              onClick={() => router.push(`/stock/${m.ticker}`)}
              className="flex w-full cursor-pointer items-center justify-between gap-3 rounded-lg px-2 py-1.5 text-left transition-colors hover:bg-bg-muted"
            >
              <div className="min-w-0 flex-1">
                <div className="flex items-baseline gap-1.5">
                  <span className="shrink-0 text-sm font-medium text-fg">{m.ticker}</span>
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
