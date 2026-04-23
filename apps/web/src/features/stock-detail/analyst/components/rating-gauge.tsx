'use client';

import { InfoTooltip } from '@/components/ui/info-tooltip';
import { cn } from '@/lib/cn';

interface RatingGaugeProps {
  score: number;
  labelKo: string;
  totalAnalysts: number;
  distribution: {
    strongBuy: number;
    buy: number;
    hold: number;
    sell: number;
    strongSell: number;
  };
}

const DIST_ROWS = [
  { key: 'strongBuy', label: '적극매수', color: 'bg-green-600' },
  { key: 'buy', label: '매수', color: 'bg-green-400' },
  { key: 'hold', label: '보유', color: 'bg-yellow-500' },
  { key: 'sell', label: '매도', color: 'bg-red-400' },
  { key: 'strongSell', label: '적극매도', color: 'bg-red-600' },
] as const;

function scoreColor(score: number) {
  if (score <= 2.5) return 'text-green-600 dark:text-green-400';
  if (score <= 3.5) return 'text-yellow-600 dark:text-yellow-400';
  return 'text-red-600 dark:text-red-400';
}

export function RatingGauge({ score, labelKo, totalAnalysts, distribution }: RatingGaugeProps) {
  const max = Math.max(
    distribution.strongBuy,
    distribution.buy,
    distribution.hold,
    distribution.sell,
    distribution.strongSell,
    1,
  );

  return (
    <div className="rounded-xl bg-bg-muted p-4">
      <div className="flex items-center justify-between">
        <h3 className="text-xs font-semibold text-fg-muted">투자 의견</h3>
        <InfoTooltip text="애널리스트들이 매수(Buy) ~ 매도(Sell) 중 어디에 투표했는지 보여줍니다. 1.0에 가까울수록 매수 의견이 강합니다." />
      </div>

      <div className="mt-3 flex items-center gap-3">
        <div className="flex h-14 w-14 flex-shrink-0 flex-col items-center justify-center rounded-full bg-bg-surface">
          <span className={cn('text-lg font-bold tabular-nums', scoreColor(score))}>
            {score.toFixed(1)}
          </span>
          <span className="text-[10px] text-fg-muted">/5.0</span>
        </div>
        <div>
          <span className={cn('text-sm font-semibold', scoreColor(score))}>{labelKo}</span>
          <p className="text-xs text-fg-muted">{totalAnalysts}명의 애널리스트</p>
        </div>
      </div>

      <div className="mt-3 space-y-1.5">
        {DIST_ROWS.map(({ key, label, color }) => {
          const count = distribution[key];
          const pct = (count / max) * 100;
          return (
            <div key={key} className="flex items-center gap-2 text-xs">
              <span className="w-14 flex-shrink-0 text-fg-muted">{label}</span>
              <div className="h-2.5 flex-1 rounded-full bg-bg-surface">
                <div
                  className={cn('h-full rounded-full transition-all', color)}
                  style={{ width: `${pct}%` }}
                />
              </div>
              <span className="w-5 text-right tabular-nums text-fg-secondary">{count}</span>
            </div>
          );
        })}
      </div>
    </div>
  );
}
