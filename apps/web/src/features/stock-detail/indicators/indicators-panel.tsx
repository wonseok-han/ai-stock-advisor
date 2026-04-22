'use client';

import { useIndicators } from '@/features/stock-detail/hooks/use-indicators';
import { cn } from '@/lib/cn';

import type { IndicatorSnapshot } from '@/types/stock';

export function IndicatorsPanel({ ticker }: { ticker: string }) {
  const { data, isLoading, error } = useIndicators(ticker);

  if (isLoading) {
    return <PanelShell>지표 계산 중…</PanelShell>;
  }
  if (error || !data) {
    return (
      <PanelShell>
        <span className="text-danger">
          지표를 계산할 수 없습니다. (시세 데이터 부족 또는 외부 서비스 지연)
        </span>
      </PanelShell>
    );
  }

  return (
    <section aria-label="참고 지표" className="card p-5">
      <h2 className="text-sm font-semibold text-fg">참고 지표</h2>
      <div className="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <RsiCard value={data.rsi14} tooltip={data.tooltipsKo.rsi14} />
        <MacdCard macd={data.macd} tooltip={data.tooltipsKo.macd} />
        <BollingerCard bollinger={data.bollinger} tooltip={data.tooltipsKo.bollinger} />
        <MaCard ma={data.movingAverage} tooltip={data.tooltipsKo.ma} />
      </div>
      <p className="mt-4 text-[11px] text-fg-muted">
        참고용 기술 지표이며, 단독 사용 시 투자 신호로 해석하지 마세요.
      </p>
    </section>
  );
}

function PanelShell({ children }: { children: React.ReactNode }) {
  return (
    <section className="card p-5 text-sm text-fg-muted">{children}</section>
  );
}

function Card({
  title,
  tooltip,
  children,
}: {
  title: string;
  tooltip: string;
  children: React.ReactNode;
}) {
  return (
    <div className="rounded-xl bg-bg-muted p-3">
      <div className="flex items-baseline justify-between">
        <span className="text-xs font-medium text-fg-muted">{title}</span>
        <InfoTooltip text={tooltip} />
      </div>
      <div className="mt-2 space-y-1 text-sm tabular-nums">{children}</div>
    </div>
  );
}

function InfoTooltip({ text }: { text: string }) {
  return (
    <span className="group relative inline-flex">
      <button
        type="button"
        aria-label={text}
        className="cursor-help rounded-full p-0.5 text-fg-muted outline-none hover:text-fg-secondary focus-visible:ring-2 focus-visible:ring-primary"
      >
        <svg
          aria-hidden="true"
          viewBox="0 0 16 16"
          className="h-3.5 w-3.5"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.5"
        >
          <circle cx="8" cy="8" r="6.5" />
          <path d="M8 7.25v3.5" strokeLinecap="round" />
          <circle cx="8" cy="5.25" r="0.6" fill="currentColor" stroke="none" />
        </svg>
      </button>
      <span
        role="tooltip"
        className="pointer-events-none invisible absolute right-0 top-full z-20 mt-1 w-72 max-w-[calc(100vw-2rem)] whitespace-normal break-keep rounded-xl border border-border bg-bg-surface px-3 py-2 text-xs leading-relaxed text-fg-secondary opacity-0 shadow-lg transition-opacity duration-100 group-hover:visible group-hover:opacity-100 group-focus-within:visible group-focus-within:opacity-100"
      >
        {text}
      </span>
    </span>
  );
}

function RsiCard({ value, tooltip }: { value: number; tooltip: string }) {
  const zone =
    value >= 70
      ? { label: '과매수 경향', cls: 'text-danger' }
      : value <= 30
        ? { label: '과매도 경향', cls: 'text-success' }
        : { label: '중립', cls: 'text-fg-muted' };
  return (
    <Card title="RSI (14)" tooltip={tooltip}>
      <div className="text-xl font-bold text-fg">{value.toFixed(1)}</div>
      <div className={cn('text-xs', zone.cls)}>{zone.label}</div>
    </Card>
  );
}

function MacdCard({
  macd,
  tooltip,
}: {
  macd: IndicatorSnapshot['macd'];
  tooltip: string;
}) {
  const histColor =
    macd.histogram > 0 ? 'text-success' : macd.histogram < 0 ? 'text-danger' : 'text-fg-muted';
  return (
    <Card title="MACD (12/26/9)" tooltip={tooltip}>
      <Row label="MACD" value={macd.macd.toFixed(2)} />
      <Row label="Signal" value={macd.signal.toFixed(2)} />
      <Row label="Hist" value={macd.histogram.toFixed(2)} valueClass={histColor} />
    </Card>
  );
}

function BollingerCard({
  bollinger,
  tooltip,
}: {
  bollinger: IndicatorSnapshot['bollinger'];
  tooltip: string;
}) {
  return (
    <Card title="Bollinger (20, 2σ)" tooltip={tooltip}>
      <Row label="Upper" value={bollinger.upper.toFixed(2)} />
      <Row label="Middle" value={bollinger.middle.toFixed(2)} />
      <Row label="Lower" value={bollinger.lower.toFixed(2)} />
      <Row label="밴드 위치" value={bollinger.percentB.toFixed(2)} />
    </Card>
  );
}

function MaCard({
  ma,
  tooltip,
}: {
  ma: IndicatorSnapshot['movingAverage'];
  tooltip: string;
}) {
  return (
    <Card title="MA (5/20/60)" tooltip={tooltip}>
      <Row label="MA5" value={ma.ma5.toFixed(2)} />
      <Row label="MA20" value={ma.ma20.toFixed(2)} />
      <Row label="MA60" value={ma.ma60.toFixed(2)} />
    </Card>
  );
}

function Row({
  label,
  value,
  valueClass,
}: {
  label: string;
  value: string;
  valueClass?: string;
}) {
  return (
    <div className="flex items-baseline justify-between gap-2">
      <span className="text-xs text-fg-muted">{label}</span>
      <span className={cn('text-sm text-fg', valueClass)}>{value}</span>
    </div>
  );
}
