'use client';

import { useIndicators } from '@/features/stock-detail/hooks/use-indicators';
import { cn } from '@/lib/cn';

import type { IndicatorSnapshot } from '@/types/stock';

/**
 * 참고 지표 패널 (design §4.2, §3.5).
 * - RSI(14), MACD(12,26,9), Bollinger(20, 2σ), MA(5/20/60)
 * - 각 지표 툴팁(BE tooltipsKo) 노출. 투자 신호로 해석하지 않도록 참고 문구 강조.
 */
export function IndicatorsPanel({ ticker }: { ticker: string }) {
  const { data, isLoading, error } = useIndicators(ticker);

  if (isLoading) {
    return <PanelShell>지표 계산 중…</PanelShell>;
  }
  if (error || !data) {
    return (
      <PanelShell>
        <span className="text-red-600">
          지표를 계산할 수 없습니다. (시세 데이터 부족 또는 외부 서비스 지연)
        </span>
      </PanelShell>
    );
  }

  return (
    <section
      aria-label="참고 지표"
      className="rounded-lg border border-zinc-200 bg-white p-4 dark:border-zinc-800 dark:bg-zinc-900"
    >
      <h2 className="mb-3 text-sm font-semibold text-zinc-700 dark:text-zinc-300">
        참고 지표
      </h2>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <RsiCard value={data.rsi14} tooltip={data.tooltipsKo.rsi14} />
        <MacdCard macd={data.macd} tooltip={data.tooltipsKo.macd} />
        <BollingerCard
          bollinger={data.bollinger}
          tooltip={data.tooltipsKo.bollinger}
        />
        <MaCard ma={data.movingAverage} tooltip={data.tooltipsKo.ma} />
      </div>
      <p className="mt-3 text-xs text-zinc-500">
        참고용 기술 지표이며, 단독 사용 시 투자 신호로 해석하지 마세요.
      </p>
    </section>
  );
}

function PanelShell({ children }: { children: React.ReactNode }) {
  return (
    <section className="rounded-lg border border-zinc-200 bg-white p-4 text-sm text-zinc-500 dark:border-zinc-800 dark:bg-zinc-900">
      {children}
    </section>
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
    <div className="rounded-md border border-zinc-100 bg-zinc-50 p-3 dark:border-zinc-800 dark:bg-zinc-950">
      <div className="flex items-baseline justify-between">
        <span className="text-xs font-medium text-zinc-500">{title}</span>
        <span
          className="cursor-help text-xs text-zinc-400"
          title={tooltip}
          aria-label={tooltip}
        >
          ⓘ
        </span>
      </div>
      <div className="mt-2 space-y-1 text-sm tabular-nums">{children}</div>
    </div>
  );
}

function RsiCard({ value, tooltip }: { value: number; tooltip: string }) {
  const zone =
    value >= 70
      ? { label: '과매수 경향', cls: 'text-red-600' }
      : value <= 30
        ? { label: '과매도 경향', cls: 'text-green-600' }
        : { label: '중립', cls: 'text-zinc-500' };
  return (
    <Card title="RSI (14)" tooltip={tooltip}>
      <div className="text-xl font-semibold text-zinc-900 dark:text-zinc-50">
        {value.toFixed(1)}
      </div>
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
    macd.histogram > 0
      ? 'text-green-600'
      : macd.histogram < 0
        ? 'text-red-600'
        : 'text-zinc-500';
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
      <Row label="%B" value={bollinger.percentB.toFixed(2)} />
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
      <span className="text-xs text-zinc-500">{label}</span>
      <span className={cn('text-sm text-zinc-900 dark:text-zinc-100', valueClass)}>
        {value}
      </span>
    </div>
  );
}
