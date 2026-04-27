'use client';

import { InfoTooltip } from '@/components/ui/info-tooltip';
import { cn } from '@/lib/cn';
import { formatUsd } from '@/lib/format/currency';

interface PriceTargetBarProps {
  current: number | null;
  high: number;
  low: number;
  mean: number;
  upsidePercent: number | null;
}

export function PriceTargetBar({ current, high, low, mean, upsidePercent }: PriceTargetBarProps) {
  const range = high - low;

  const currentPct =
    current != null && range > 0
      ? Math.min(Math.max(((current - low) / range) * 100, 0), 100)
      : null;

  const meanPct = range > 0 ? Math.min(Math.max(((mean - low) / range) * 100, 0), 100) : 50;

  const isUpside = upsidePercent != null && upsidePercent >= 0;

  return (
    <div className="rounded-xl bg-bg-muted p-4">
      <div className="flex items-center justify-between">
        <h3 className="text-xs font-semibold text-fg-muted">목표가</h3>
        <InfoTooltip text="애널리스트들이 예상하는 향후 12개월 목표 주가입니다. 현재가와 비교해 상승(Upside) 또는 하락(Downside) 여력을 확인할 수 있습니다." />
      </div>

      <div className="mt-3 flex items-baseline justify-between">
        <div className="text-xs text-fg-muted">
          {current != null && (
            <>
              현재가 <span className="font-semibold text-fg">{formatUsd(current)}</span>
            </>
          )}
        </div>
        <div className="text-right">
          <span className="text-xs text-fg-muted">평균 목표가 </span>
          <span className="text-sm font-semibold text-fg">{formatUsd(mean)}</span>
          {upsidePercent != null && (
            <span
              className={cn(
                'ml-1.5 inline-block rounded-md px-1.5 py-0.5 text-xs font-medium',
                isUpside
                  ? 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400'
                  : 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400',
              )}
            >
              {isUpside ? '+' : ''}
              {upsidePercent.toFixed(1)}%
            </span>
          )}
        </div>
      </div>

      <div className="relative mt-3 h-2.5 rounded-full bg-bg-surface">
        {currentPct != null && (
          <div
            className="absolute top-1/2 h-4 w-4 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-bg-surface bg-primary"
            style={{ left: `${currentPct}%` }}
            aria-label={`현재가 위치: ${currentPct.toFixed(0)}%`}
          />
        )}
        <div
          className="absolute top-1/2 h-3.5 w-3.5 -translate-x-1/2 -translate-y-1/2 rotate-45 border-2 border-bg-surface bg-accent"
          style={{ left: `${meanPct}%` }}
          aria-label={`평균 목표가 위치: ${meanPct.toFixed(0)}%`}
        />
      </div>

      <div className="mt-1.5 flex items-center justify-between text-[10px] text-fg-muted">
        <span>Low {formatUsd(low)}</span>
        <span>High {formatUsd(high)}</span>
      </div>

      <div className="mt-2 flex items-center gap-3 text-[10px] text-fg-muted">
        {currentPct != null && (
          <span className="flex items-center gap-1">
            <span className="inline-block h-2.5 w-2.5 rounded-full bg-primary" />
            현재가
          </span>
        )}
        <span className="flex items-center gap-1">
          <span className="inline-block h-2.5 w-2.5 rotate-45 bg-accent" />
          평균 목표가
        </span>
      </div>
    </div>
  );
}
