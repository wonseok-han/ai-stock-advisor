'use client';

import { useSectorPerformance } from '@/features/market-dashboard/hooks/use-sector-performance';
import { InfoTooltip } from '@/components/ui/info-tooltip';
import { cn } from '@/lib/cn';

export function SectorPerformance() {
  const { data, isLoading, error } = useSectorPerformance();

  if (isLoading) {
    return (
      <section aria-label="섹터 퍼포먼스" className="card overflow-hidden">
        <div className="border-b border-border px-5 py-4">
          <div className="flex items-center gap-1.5">
            <h2 className="text-xs font-medium uppercase tracking-wide text-fg-muted">
              섹터 퍼포먼스
            </h2>
          </div>
        </div>
        <div className="space-y-2 p-5">
          {Array.from({ length: 6 }).map((_, i) => (
            <div key={i} className="h-7 animate-pulse rounded bg-bg-skeleton" />
          ))}
        </div>
      </section>
    );
  }

  if (error || !data || data.length === 0) {
    return null;
  }

  const sorted = [...data].sort((a, b) => b.changePercent - a.changePercent);
  const maxAbs = Math.max(...sorted.map((s) => Math.abs(s.changePercent)), 0.01);

  return (
    <section aria-label="섹터 퍼포먼스" className="card overflow-hidden">
      <div className="border-b border-border px-5 py-4">
        <div className="flex items-center gap-1.5">
          <h2 className="text-sm font-semibold text-fg">
            섹터 퍼포먼스
          </h2>
          <InfoTooltip text="미국 주식 시장 11개 GICS 섹터의 일간 변동률입니다. 어떤 업종이 강하고 약한지 한눈에 파악할 수 있습니다." />
        </div>
      </div>
      <div className="space-y-1.5 p-5">
        {sorted.map((s) => (
          <SectorBar key={s.sector} sector={s} maxAbs={maxAbs} />
        ))}
      </div>
    </section>
  );
}

function SectorBar({
  sector,
  maxAbs,
}: {
  sector: { sectorKo: string; changePercent: number };
  maxAbs: number;
}) {
  const up = sector.changePercent > 0;
  const down = sector.changePercent < 0;
  const widthPercent = Math.min((Math.abs(sector.changePercent) / maxAbs) * 100, 100);

  const barColor = up ? 'bg-emerald-500/70' : down ? 'bg-red-500/70' : 'bg-fg-muted/30';
  const textColor = up ? 'text-success' : down ? 'text-danger' : 'text-fg-muted';

  return (
    <div className="flex items-center gap-2">
      <span className="w-[72px] flex-shrink-0 text-right text-xs text-fg-secondary">
        {sector.sectorKo}
      </span>
      <div className="relative flex h-7 flex-1 items-center">
        <div className="absolute inset-0 rounded-md bg-bg-muted/50" />
        <div
          className={cn('relative h-full rounded-md transition-all duration-500', barColor)}
          style={{ width: `${widthPercent}%`, minWidth: widthPercent > 0 ? '4px' : '0' }}
        />
      </div>
      <span className={cn('w-[52px] flex-shrink-0 text-right text-xs font-semibold tabular-nums', textColor)}>
        {sector.changePercent > 0 ? '+' : ''}
        {sector.changePercent.toFixed(2)}%
      </span>
    </div>
  );
}
