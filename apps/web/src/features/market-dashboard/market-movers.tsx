'use client';

import { useRouter } from 'next/navigation';

import { useMarketMovers } from '@/features/market-dashboard/hooks/use-market-movers';
import { cn } from '@/lib/cn';
import { formatUsd } from '@/lib/format/currency';
import { formatCompact } from '@/lib/format/number';
import { formatPercentChange } from '@/lib/format/percent';

import type { MarketMover } from '@/types/market';

/**
 * 급등/급락 종목 2-column 테이블.
 * 종목 행 클릭 → /stock/[ticker] 이동.
 * design §7.3
 */
export function MarketMovers() {
  const { data, isLoading, error, refetch } = useMarketMovers();

  if (isLoading) {
    return (
      <section
        aria-label="급등/급락 종목"
        className="rounded-lg border border-zinc-200 bg-white p-4 dark:border-zinc-800 dark:bg-zinc-900"
      >
        <div className="h-4 w-32 animate-pulse rounded bg-zinc-100 dark:bg-zinc-800" />
        <div className="mt-3 space-y-2">
          {Array.from({ length: 5 }).map((_, i) => (
            <div
              key={i}
              className="h-8 animate-pulse rounded bg-zinc-100 dark:bg-zinc-800"
            />
          ))}
        </div>
      </section>
    );
  }

  if (error || !data) {
    return (
      <section className="rounded-lg border border-zinc-200 bg-white p-4 dark:border-zinc-800 dark:bg-zinc-900">
        <p className="text-sm text-red-600">
          급등/급락 데이터를 불러올 수 없습니다.
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
    <section
      aria-label="급등/급락 종목"
      className="rounded-lg border border-zinc-200 bg-white p-4 dark:border-zinc-800 dark:bg-zinc-900"
    >
      <h2 className="mb-3 text-sm font-semibold text-zinc-700 dark:text-zinc-300">
        급등/급락 종목
      </h2>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <MoverList title="급등" movers={data.gainers} variant="gain" />
        <MoverList title="급락" movers={data.losers} variant="loss" />
      </div>
      <p className="mt-3 text-xs text-zinc-500">
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
  const titleColor =
    variant === 'gain'
      ? 'text-green-600 dark:text-green-500'
      : 'text-red-600 dark:text-red-500';

  if (movers.length === 0) {
    return (
      <div>
        <h3 className={cn('mb-2 text-xs font-medium', titleColor)}>
          {title}
        </h3>
        <p className="text-xs text-zinc-400">데이터 없음</p>
      </div>
    );
  }

  return (
    <div>
      <h3 className={cn('mb-2 text-xs font-medium', titleColor)}>{title}</h3>
      <ul className="flex flex-col divide-y divide-zinc-100 dark:divide-zinc-800">
        {movers.map((m) => (
          <li key={m.ticker}>
            <button
              onClick={() => router.push(`/stock/${m.ticker}`)}
              className="flex w-full items-center justify-between gap-2 py-1.5 text-left hover:bg-zinc-50 dark:hover:bg-zinc-800/50"
            >
              <div className="min-w-0">
                <span className="text-sm font-medium text-zinc-900 dark:text-zinc-100">
                  {m.ticker}
                </span>
                <span className="ml-1.5 truncate text-xs text-zinc-500">
                  {m.name}
                </span>
              </div>
              <div className="shrink-0 text-right">
                <span className="text-xs tabular-nums text-zinc-600 dark:text-zinc-400">
                  {formatUsd(m.price)}
                </span>
                <span
                  className={cn(
                    'ml-1.5 text-xs tabular-nums font-medium',
                    variant === 'gain'
                      ? 'text-green-600 dark:text-green-500'
                      : 'text-red-600 dark:text-red-500',
                  )}
                >
                  {formatPercentChange(m.changePercent)}
                </span>
                {m.volume > 0 && (
                  <span className="ml-1.5 text-[10px] tabular-nums text-zinc-400">
                    {formatCompact(m.volume)}
                  </span>
                )}
              </div>
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}
