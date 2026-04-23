'use client';

import { InfoTooltip } from '@/components/ui/info-tooltip';
import { cn } from '@/lib/cn';
import { formatUsd } from '@/lib/format/currency';

interface EarningsHistoryProps {
  earnings: {
    quarter: string;
    epsActual: number;
    epsEstimate: number;
    surprisePercent: number;
    result: 'BEAT' | 'MISS' | 'MEET' | 'EST';
  }[];
}

const RESULT_STYLE = {
  BEAT: 'text-green-600 dark:text-green-400',
  MISS: 'text-red-600 dark:text-red-400',
  MEET: 'text-yellow-600 dark:text-yellow-400',
  EST: 'text-blue-600 dark:text-blue-400',
} as const;

const RESULT_LABEL = {
  BEAT: '초과',
  MISS: '미달',
  MEET: '부합',
  EST: '전망',
} as const;

export function EarningsHistory({ earnings }: EarningsHistoryProps) {
  if (earnings.length === 0) return null;

  return (
    <div className="rounded-xl bg-bg-muted p-4">
      <div className="flex items-center justify-between">
        <h3 className="text-xs font-semibold text-fg-muted">분기 실적</h3>
        <InfoTooltip text="최근 4분기 주당순이익(EPS) 실적입니다. 예상치를 넘으면 Beat(초과), 못 미치면 Miss(미달)입니다. 실적 발표는 주가에 큰 영향을 줍니다." />
      </div>

      {/* Desktop table */}
      <div className="mt-3 hidden sm:block">
        <div className="grid grid-cols-[1fr_auto_auto_auto_auto] gap-x-3 text-xs">
          <div className="text-fg-muted">분기</div>
          <div className="text-right text-fg-muted">예상</div>
          <div className="text-right text-fg-muted">실제</div>
          <div className="text-right text-fg-muted">서프라이즈</div>
          <div className="text-right text-fg-muted">결과</div>

          {earnings.map((e) => (
            <div key={e.quarter} className="contents">
              <div className="border-t border-border-muted py-1.5 font-medium text-fg">
                {e.quarter}
              </div>
              <div className="border-t border-border-muted py-1.5 text-right tabular-nums text-fg-secondary">
                {formatUsd(e.epsEstimate)}
              </div>
              <div className="border-t border-border-muted py-1.5 text-right tabular-nums font-medium text-fg">
                {formatUsd(e.epsActual)}
              </div>
              <div
                className={cn(
                  'border-t border-border-muted py-1.5 text-right tabular-nums font-medium',
                  RESULT_STYLE[e.result],
                )}
              >
                {e.surprisePercent > 0 ? '+' : ''}
                {e.surprisePercent.toFixed(1)}%
              </div>
              <div
                className={cn(
                  'border-t border-border-muted py-1.5 text-right font-semibold',
                  RESULT_STYLE[e.result],
                )}
              >
                {RESULT_LABEL[e.result]}
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Mobile cards */}
      <div className="mt-3 space-y-2 sm:hidden">
        {earnings.map((e) => (
          <div key={e.quarter} className="flex items-center justify-between rounded-lg bg-bg-surface px-3 py-2">
            <div>
              <div className="text-xs font-medium text-fg">{e.quarter}</div>
              <div className="mt-0.5 text-[10px] text-fg-muted">
                예상 {formatUsd(e.epsEstimate)} → 실제 {formatUsd(e.epsActual)}
              </div>
            </div>
            <div className="text-right">
              <div className={cn('text-xs font-semibold', RESULT_STYLE[e.result])}>
                {RESULT_LABEL[e.result]}
              </div>
              <div className={cn('text-[10px] tabular-nums', RESULT_STYLE[e.result])}>
                {e.surprisePercent > 0 ? '+' : ''}
                {e.surprisePercent.toFixed(1)}%
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
