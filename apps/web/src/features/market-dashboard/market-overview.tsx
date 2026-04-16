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
              className="h-24 animate-pulse rounded-lg bg-zinc-100 dark:bg-zinc-800"
            />
          ))}
        </div>
        <div className="h-8 animate-pulse rounded-lg bg-zinc-100 dark:bg-zinc-800" />
      </section>
    );
  }

  if (error || !data) {
    return (
      <section className="rounded-lg border border-zinc-200 bg-white p-4 dark:border-zinc-800 dark:bg-zinc-900">
        <p className="text-sm text-red-600">
          시장 데이터를 불러올 수 없습니다.
        </p>
        <button
          onClick={() => refetch()}
          className="mt-2 text-xs text-blue-600 hover:underline dark:text-blue-400"
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
        <div className="flex items-center gap-2 rounded-lg border border-zinc-200 bg-white px-4 py-2 text-sm dark:border-zinc-800 dark:bg-zinc-900">
          <span className="text-zinc-500">USD/KRW</span>
          <span className="font-medium tabular-nums text-zinc-900 dark:text-zinc-50">
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
                  ? 'text-green-600 dark:text-green-500'
                  : 'text-red-600 dark:text-red-500',
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
    ? 'text-green-600 dark:text-green-500'
    : down
      ? 'text-red-600 dark:text-red-500'
      : 'text-zinc-500';

  const vixLevel =
    isVix && index.price >= 30
      ? 'border-red-300 dark:border-red-700'
      : isVix && index.price >= 20
        ? 'border-amber-300 dark:border-amber-700'
        : 'border-zinc-200 dark:border-zinc-800';

  return (
    <div
      className={cn(
        'rounded-lg border bg-white p-3 dark:bg-zinc-900',
        vixLevel,
      )}
    >
      <div className="text-xs text-zinc-500">{index.name}</div>
      <div className="mt-1 text-lg font-semibold tabular-nums text-zinc-900 dark:text-zinc-50">
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
