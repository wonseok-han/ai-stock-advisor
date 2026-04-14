'use client';

import { useAiSignal } from '@/features/ai-signal/hooks/use-ai-signal';
import { cn } from '@/lib/cn';

import type { AiSignal, AiSignalClass } from '@/types/ai-signal';
import type { TimeFrame } from '@/types/stock';

/**
 * AI 시그널 패널 (design §4.1, §4.2).
 * - 5-class 시그널 + 신뢰도 + 근거/리스크 + 중립 fallback 시 안내 배너
 * - 면책 문구 상시 노출. 숫자 confidence 는 바/%로 시각화.
 */
export function AiSignalPanel({ ticker, tf }: { ticker: string; tf: TimeFrame }) {
  const { data, isLoading, error } = useAiSignal(ticker, tf);

  if (isLoading) {
    return <PanelShell>AI 분석 생성 중… (최대 5초 소요)</PanelShell>;
  }
  if (error || !data) {
    return (
      <PanelShell>
        <span className="text-red-600">
          AI 분석을 불러올 수 없습니다. 잠시 후 다시 시도해주세요.
        </span>
      </PanelShell>
    );
  }

  return (
    <section
      aria-label="AI 참고 분석"
      className="rounded-lg border border-zinc-200 bg-white p-4 dark:border-zinc-800 dark:bg-zinc-900"
    >
      <div className="mb-3 flex items-center justify-between">
        <h2 className="text-sm font-semibold text-zinc-700 dark:text-zinc-300">
          AI 참고 분석
        </h2>
        <TimeframeBadge timeframe={data.timeframe} />
      </div>

      {data.fallback ? (
        <div className="mb-3 rounded-md border border-amber-200 bg-amber-50 p-2 text-xs text-amber-800 dark:border-amber-900/40 dark:bg-amber-950/40 dark:text-amber-200">
          일시적으로 AI 분석이 제한되어 중립 관점으로 제공됩니다.
        </div>
      ) : null}

      <SignalHero signal={data.signal} confidence={data.confidence} />

      <p className="mt-3 text-sm text-zinc-700 dark:text-zinc-300">
        {data.summaryKo}
      </p>

      <div className="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-2">
        <List title="근거" items={data.rationale} tone="neutral" />
        <List title="리스크" items={data.risks} tone="warning" />
      </div>

      <p className="mt-3 text-xs text-zinc-500">{data.disclaimer}</p>
      <p className="mt-1 text-[11px] text-zinc-400">
        모델: {data.modelName} · 생성: {new Date(data.generatedAt).toLocaleString('ko-KR')}
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

function TimeframeBadge({ timeframe }: { timeframe: AiSignal['timeframe'] }) {
  const label =
    timeframe === 'SHORT' ? '단기' : timeframe === 'MID' ? '중기' : '장기';
  return (
    <span className="rounded-sm bg-zinc-100 px-1.5 py-0.5 text-[10px] text-zinc-600 dark:bg-zinc-800 dark:text-zinc-300">
      {label} 관점
    </span>
  );
}

function SignalHero({
  signal,
  confidence,
}: {
  signal: AiSignalClass;
  confidence: number;
}) {
  const pct = Math.round(confidence * 100);
  const { label, barCls, badgeCls } = mapSignal(signal);
  return (
    <div className="rounded-md border border-zinc-100 bg-zinc-50 p-3 dark:border-zinc-800 dark:bg-zinc-950">
      <div className="flex items-center justify-between">
        <span
          className={cn(
            'rounded-sm px-2 py-0.5 text-xs font-semibold',
            badgeCls,
          )}
        >
          {label}
        </span>
        <span className="text-xs text-zinc-500">신뢰도 {pct}%</span>
      </div>
      <div className="mt-2 h-1.5 w-full overflow-hidden rounded-full bg-zinc-200 dark:bg-zinc-800">
        <div
          className={cn('h-full rounded-full', barCls)}
          style={{ width: `${pct}%` }}
        />
      </div>
    </div>
  );
}

function mapSignal(signal: AiSignalClass): {
  label: string;
  barCls: string;
  badgeCls: string;
} {
  switch (signal) {
    case 'STRONG_BUY':
      return {
        label: '강한 긍정',
        barCls: 'bg-green-500',
        badgeCls:
          'bg-green-100 text-green-800 dark:bg-green-900/40 dark:text-green-200',
      };
    case 'BUY':
      return {
        label: '긍정',
        barCls: 'bg-green-400',
        badgeCls:
          'bg-green-50 text-green-700 dark:bg-green-950/50 dark:text-green-300',
      };
    case 'NEUTRAL':
      return {
        label: '중립',
        barCls: 'bg-zinc-400',
        badgeCls:
          'bg-zinc-100 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-200',
      };
    case 'SELL':
      return {
        label: '부정',
        barCls: 'bg-red-400',
        badgeCls: 'bg-red-50 text-red-700 dark:bg-red-950/50 dark:text-red-300',
      };
    case 'STRONG_SELL':
      return {
        label: '강한 부정',
        barCls: 'bg-red-500',
        badgeCls: 'bg-red-100 text-red-800 dark:bg-red-900/40 dark:text-red-200',
      };
  }
}

function List({
  title,
  items,
  tone,
}: {
  title: string;
  items: string[];
  tone: 'neutral' | 'warning';
}) {
  const dotCls =
    tone === 'warning' ? 'bg-amber-400' : 'bg-zinc-300 dark:bg-zinc-600';
  return (
    <div className="rounded-md border border-zinc-100 bg-zinc-50 p-3 dark:border-zinc-800 dark:bg-zinc-950">
      <h3 className="mb-2 text-xs font-semibold text-zinc-500">{title}</h3>
      <ul className="flex flex-col gap-1.5">
        {items.map((text, idx) => (
          <li
            key={`${title}-${idx}`}
            className="flex items-start gap-2 text-xs text-zinc-700 dark:text-zinc-300"
          >
            <span
              className={cn('mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full', dotCls)}
              aria-hidden="true"
            />
            <span>{text}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
