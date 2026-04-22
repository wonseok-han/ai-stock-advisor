'use client';

import { useMarketOverview } from '@/features/market-dashboard/hooks/use-market-overview';
import { cn } from '@/lib/cn';
import { formatPercentChange, formatSignedNumber } from '@/lib/format/percent';

import type { MarketIndex } from '@/types/market';

export function MarketOverview() {
  const { data, isLoading, error, refetch } = useMarketOverview();

  if (isLoading) {
    return (
      <section aria-label="시장 개요" className="space-y-3">
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 lg:grid-cols-5">
          {Array.from({ length: 5 }).map((_, i) => (
            <div key={i} className="h-20 animate-pulse rounded-xl bg-bg-muted" />
          ))}
        </div>
      </section>
    );
  }

  if (error || !data) {
    return (
      <section className="card p-4">
        <p className="text-sm text-danger">시장 데이터를 불러올 수 없습니다.</p>
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
    <section aria-label="시장 개요">
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 lg:grid-cols-5">
        {data.indices.map((idx) => (
          <IndexCard key={idx.symbol} index={idx} />
        ))}
        {data.usdKrw != null && (
          <UsdKrwCard price={data.usdKrw} change={data.usdKrwChange} />
        )}
      </div>
    </section>
  );
}

function IndexCard({ index }: { index: MarketIndex }) {
  const up = index.change > 0;
  const down = index.change < 0;
  const isVix = index.name === 'VIX';

  const changeColor = up ? 'text-success' : down ? 'text-danger' : 'text-fg-muted';
  const changeBg = up
    ? 'bg-emerald-500/10 text-success'
    : down
      ? 'bg-red-500/10 text-danger'
      : 'bg-bg-muted text-fg-muted';

  const vixHighlight =
    isVix && index.price >= 30
      ? 'ring-1 ring-red-500/30'
      : isVix && index.price >= 20
        ? 'ring-1 ring-amber-500/30'
        : '';

  return (
    <div className={cn('card p-3', vixHighlight)}>
      <div className="flex items-center justify-between">
        <span className="text-xs font-medium text-fg-muted">{index.name}</span>
        <span className={cn('rounded-md px-1.5 py-0.5 text-[10px] font-semibold tabular-nums', changeBg)}>
          {formatPercentChange(index.changePercent)}
        </span>
      </div>
      <div className="mt-1.5 text-lg font-semibold tabular-nums text-fg">
        {index.price.toLocaleString('en-US', {
          minimumFractionDigits: 2,
          maximumFractionDigits: 2,
        })}
      </div>
      <div className={cn('text-xs tabular-nums', changeColor)}>
        {formatSignedNumber(index.change)}
      </div>
    </div>
  );
}

function UsdKrwCard({ price, change }: { price: number; change?: number | null }) {
  const up = change != null && change > 0;
  const down = change != null && change < 0;
  const changeBg = up
    ? 'bg-emerald-500/10 text-success'
    : down
      ? 'bg-red-500/10 text-danger'
      : 'bg-bg-muted text-fg-muted';

  return (
    <div className="card p-3">
      <div className="flex items-center justify-between">
        <span className="text-xs font-medium text-fg-muted">USD/KRW</span>
        {change != null && change !== 0 && (
          <span className={cn('rounded-md px-1.5 py-0.5 text-[10px] font-semibold tabular-nums', changeBg)}>
            {formatSignedNumber(change)}
          </span>
        )}
      </div>
      <div className="mt-1.5 text-lg font-semibold tabular-nums text-fg">
        {price.toLocaleString('ko-KR', {
          minimumFractionDigits: 2,
          maximumFractionDigits: 2,
        })}
      </div>
      <div className="text-xs text-fg-muted">원</div>
    </div>
  );
}
