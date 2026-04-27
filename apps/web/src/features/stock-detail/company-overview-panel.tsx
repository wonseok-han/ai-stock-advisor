'use client';

import { useState } from 'react';

import { InfoTooltip } from '@/components/ui/info-tooltip';
import { PanelLoading } from '@/components/ui/panel-loading';
import { useCompanyOverview } from '@/features/stock-detail/hooks/use-company-overview';
import { useQuote } from '@/features/stock-detail/hooks/use-quote';
import { formatUsd } from '@/lib/format/currency';
import { formatMarketCap, formatRatio, formatEmployees } from '@/lib/format/number';
import { cn } from '@/lib/cn';

export function CompanyOverviewPanel({ ticker }: { ticker: string }) {
  const { data: overview, isLoading, error } = useCompanyOverview(ticker);
  const { data: quote } = useQuote(ticker);

  if (isLoading) {
    return <PanelLoading title="기업 개요" text="기업 정보를 불러오고 있어요" />;
  }
  if (error || !overview) {
    return null;
  }

  const w52High = overview.week52High ?? quote?.week52High;
  const w52Low = overview.week52Low ?? quote?.week52Low;

  return (
    <section aria-label="기업 개요" className="card p-5">
      <h2 className="text-sm font-semibold text-fg">기업 개요</h2>

      <div className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
        <StatCell label="섹터" value={overview.sector} tooltip="기업이 속한 대분류 산업군입니다." />
        <StatCell label="산업" value={overview.industry} tooltip="기업이 속한 세부 산업 분류입니다." />
        <StatCell label="시가총액" value={formatMarketCap(overview.marketCap)} tooltip="현재 주가 × 발행 주식 수. 기업의 시장 가치를 나타냅니다. Large Cap($10B+), Mid Cap($2B~$10B), Small Cap($2B 미만)으로 구분합니다." />
        <StatCell label="P/E" value={formatRatio(overview.peRatio)} tooltip="주가수익비율(Price-to-Earnings). 주가를 주당순이익(EPS)으로 나눈 값입니다. 높으면 시장이 미래 성장을 기대한다는 뜻이고, 낮으면 저평가 가능성이 있습니다. 동종 업계 비교가 중요합니다." />
        <StatCell label="EPS" value={overview.eps != null ? formatUsd(overview.eps) : '—'} tooltip="주당순이익(Earnings Per Share). 기업의 순이익을 발행 주식 수로 나눈 값입니다. 높을수록 주당 벌어들이는 이익이 큽니다." />
        <StatCell label="배당금" value={overview.dividendPerShare != null ? formatUsd(overview.dividendPerShare) : '—'} tooltip="주당 연간 배당금입니다. 배당금이 없으면 성장에 재투자하는 기업일 수 있습니다." />
        <StatCell label="베타" value={formatRatio(overview.beta)} tooltip="시장(S&P 500) 대비 변동성 지표입니다. 1.0이면 시장과 동일, 1.0 초과면 시장보다 변동이 크고, 1.0 미만이면 상대적으로 안정적입니다." />
        <StatCell label="직원 수" value={formatEmployees(overview.employees)} tooltip="기업의 정규직 직원 수입니다. 기업 규모를 가늠하는 참고 지표입니다." />
      </div>

      {w52High != null && w52Low != null && (
        <Week52RangeBar high={w52High} low={w52Low} current={quote?.price ?? null} />
      )}

      {overview.description && (
        <CollapsibleDescription text={overview.description} />
      )}

      {(overview.website || overview.ipoDate) && (
        <div className="mt-3 flex flex-wrap gap-4 text-xs text-fg-muted">
          {overview.website && (
            <a
              href={overview.website}
              target="_blank"
              rel="noopener noreferrer"
              className="underline hover:text-fg-secondary"
            >
              {new URL(overview.website).hostname}
            </a>
          )}
          {overview.ipoDate && <span>IPO: {overview.ipoDate}</span>}
        </div>
      )}
    </section>
  );
}

function StatCell({ label, value, tooltip }: { label: string; value: string | null | undefined; tooltip: string }) {
  return (
    <div className="rounded-xl bg-bg-muted p-3">
      <div className="flex items-baseline justify-between">
        <span className="text-xs text-fg-muted">{label}</span>
        <InfoTooltip text={tooltip} />
      </div>
      <div className="mt-1 text-sm font-semibold tabular-nums text-fg">
        {value ?? '—'}
      </div>
    </div>
  );
}

function Week52RangeBar({
  high,
  low,
  current,
}: {
  high: number;
  low: number;
  current: number | null;
}) {
  const range = high - low;
  const pct = current != null && range > 0
    ? Math.min(Math.max(((current - low) / range) * 100, 0), 100)
    : null;

  return (
    <div className="mt-4">
      <div className="flex items-center justify-between text-xs text-fg-muted">
        <span>52주 최저 {formatUsd(low)}</span>
        <span>52주 최고 {formatUsd(high)}</span>
      </div>
      <div className="relative mt-1 h-2 rounded-full bg-bg-muted">
        <div
          className="absolute inset-y-0 left-0 rounded-full bg-primary/30"
          style={{ width: `${pct ?? 0}%` }}
        />
        {pct != null && (
          <div
            className={cn(
              'absolute top-1/2 h-3.5 w-3.5 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-bg-surface bg-primary',
            )}
            style={{ left: `${pct}%` }}
            aria-label={`현재가 위치: ${pct.toFixed(0)}%`}
          />
        )}
      </div>
    </div>
  );
}

function CollapsibleDescription({ text }: { text: string }) {
  const [expanded, setExpanded] = useState(false);
  const truncated = text.length > 200;

  return (
    <div className="mt-4">
      <p className={cn('text-xs leading-relaxed text-fg-secondary', !expanded && truncated && 'line-clamp-3')}>
        {text}
      </p>
      {truncated && (
        <button
          type="button"
          onClick={() => setExpanded((v) => !v)}
          className="mt-1 text-xs font-medium text-primary hover:underline"
        >
          {expanded ? '접기' : '더보기'}
        </button>
      )}
    </div>
  );
}
