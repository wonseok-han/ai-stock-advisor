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

const RESULT_BADGE = {
  BEAT: 'bg-emerald-500/10 text-success',
  MISS: 'bg-red-500/10 text-danger',
  MEET: 'bg-bg-surface text-fg-secondary',
  EST: 'bg-sky-500/10 text-sky-600 dark:text-sky-400',
} as const;

const RESULT_LABEL = {
  BEAT: '초과',
  MISS: '미달',
  MEET: '부합',
  EST: '전망',
} as const;

function formatQuarter(raw: string): string {
  const d = new Date(raw);
  if (isNaN(d.getTime())) return raw;
  const y = String(d.getFullYear()).slice(2);
  const m = d.getMonth() + 1;
  const q = m <= 3 ? 1 : m <= 6 ? 2 : m <= 9 ? 3 : 4;
  return `'${y} Q${q}`;
}

export function EarningsHistory({ earnings }: EarningsHistoryProps) {
  if (earnings.length === 0) return null;

  const maxEps = Math.max(...earnings.map((e) => Math.max(Math.abs(e.epsActual), Math.abs(e.epsEstimate))), 0.01);

  return (
    <div className="rounded-xl bg-bg-muted p-4">
      <div className="flex items-center justify-between">
        <h3 className="text-xs font-semibold text-fg-muted">분기 실적</h3>
        <InfoTooltip text="최근 4분기 주당순이익(EPS) 실적입니다. 예상치를 넘으면 Beat(초과), 못 미치면 Miss(미달)입니다. 실적 발표는 주가에 큰 영향을 줍니다." />
      </div>

      <div className="mt-3 flex flex-col gap-2">
        {earnings.map((e) => {
          const estPct = (Math.abs(e.epsEstimate) / maxEps) * 100;
          const actPct = (Math.abs(e.epsActual) / maxEps) * 100;
          return (
            <div key={e.quarter} className="rounded-lg bg-bg-surface p-3">
              <div className="flex items-center justify-between">
                <span className="text-xs font-semibold text-fg">
                  {formatQuarter(e.quarter)}
                </span>
                <span
                  className={cn(
                    'rounded-md px-2 py-0.5 text-[11px] font-semibold',
                    RESULT_BADGE[e.result],
                  )}
                >
                  {RESULT_LABEL[e.result]}{' '}
                  {e.result !== 'MEET' && (
                    <span className="tabular-nums">
                      {e.surprisePercent > 0 ? '+' : ''}
                      {e.surprisePercent.toFixed(1)}%
                    </span>
                  )}
                </span>
              </div>

              <div className="mt-2 space-y-1.5">
                <div className="flex items-center gap-2">
                  <span className="w-7 text-[10px] text-fg-muted">예상</span>
                  <div className="h-3.5 flex-1 overflow-hidden rounded-sm bg-bg-muted">
                    <div
                      className="h-full rounded-sm bg-fg-muted/25"
                      style={{ width: `${estPct}%` }}
                    />
                  </div>
                  <span className="w-12 text-right text-[11px] tabular-nums text-fg-secondary">
                    {formatUsd(e.epsEstimate)}
                  </span>
                </div>
                <div className="flex items-center gap-2">
                  <span className="w-7 text-[10px] text-fg-muted">실제</span>
                  <div className="h-3.5 flex-1 overflow-hidden rounded-sm bg-bg-muted">
                    <div
                      className={cn(
                        'h-full rounded-sm',
                        e.result === 'BEAT'
                          ? 'bg-success/40'
                          : e.result === 'MISS'
                            ? 'bg-danger/40'
                            : 'bg-fg-muted/30',
                      )}
                      style={{ width: `${actPct}%` }}
                    />
                  </div>
                  <span className="w-12 text-right text-[11px] font-medium tabular-nums text-fg">
                    {formatUsd(e.epsActual)}
                  </span>
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
