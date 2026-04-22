'use client';

import { useMarketOverview } from '@/features/market-dashboard/hooks/use-market-overview';
import { cn } from '@/lib/cn';
import { formatPercentChange, formatSignedNumber } from '@/lib/format/percent';

import type { MarketOverview } from '@/types/market';

import type { MarketIndex } from '@/types/market';

/**
 * 시장 개요: 지수 카드(S&P500, Nasdaq, Dow, VIX) + USD/KRW 환율.
 * design §7.2
 */
export function MarketOverview() {
  const { data, isLoading, error, refetch } = useMarketOverview();

  if (isLoading) {
    return (
      <section aria-label="시장 개요" className="space-y-3">
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          {Array.from({ length: 4 }).map((_, i) => (
            <div
              key={i}
              className="h-24 animate-pulse rounded-lg bg-bg-muted"
            />
          ))}
        </div>
        <div className="h-8 animate-pulse rounded-lg bg-bg-muted" />
      </section>
    );
  }

  if (error || !data) {
    return (
      <section className="rounded-lg border border-border bg-bg-surface p-4">
        <p className="text-sm text-danger">
          시장 데이터를 불러올 수 없습니다.
        </p>
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
    <section aria-label="시장 개요" className="space-y-3">
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        {data.indices.map((idx) => (
          <IndexCard key={idx.symbol} index={idx} />
        ))}
      </div>
      {data.usdKrw != null && (
        <div className="flex items-center gap-2 rounded-lg border border-border bg-bg-surface px-4 py-2 text-sm">
          <span className="text-fg-muted">USD/KRW</span>
          <span className="font-medium tabular-nums text-fg">
            {data.usdKrw.toLocaleString('ko-KR', {
              minimumFractionDigits: 2,
              maximumFractionDigits: 2,
            })}
          </span>
          {data.usdKrwChange != null && data.usdKrwChange !== 0 && (
            <span
              className={cn(
                'text-xs tabular-nums font-medium',
                data.usdKrwChange > 0
                  ? 'text-success'
                  : 'text-danger',
              )}
            >
              {formatSignedNumber(data.usdKrwChange)}
            </span>
          )}
        </div>
      )}
    </section>
  );
}

function IndexCard({ index }: { index: MarketIndex }) {
  const up = index.change > 0;
  const down = index.change < 0;
  const isVix = index.name === 'VIX';

  const changeColor = up
    ? 'text-success'
    : down
      ? 'text-danger'
      : 'text-fg-muted';

  const vixLevel =
    isVix && index.price >= 30
      ? 'border-red-300 dark:border-red-700'
      : isVix && index.price >= 20
        ? 'border-amber-300 dark:border-amber-700'
        : 'border-border';

  return (
    <div
      className={cn(
        'rounded-lg border bg-bg-surface p-3',
        vixLevel,
      )}
    >
      <div className="text-xs text-fg-muted">{index.name}</div>
      <div className="mt-1 text-lg font-semibold tabular-nums text-fg">
        {index.price.toLocaleString('en-US', {
          minimumFractionDigits: 2,
          maximumFractionDigits: 2,
        })}
      </div>
      <div className={cn('text-xs tabular-nums', changeColor)}>
        {formatSignedNumber(index.change)} (
        {formatPercentChange(index.changePercent)})
      </div>
    </div>
  );
}
