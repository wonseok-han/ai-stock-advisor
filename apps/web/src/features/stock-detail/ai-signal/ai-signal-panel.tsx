'use client';

import Link from 'next/link';

import { useAuth } from '@/features/auth/auth-provider';
import { BeginnerExplanation } from '@/features/stock-detail/ai-signal/components/beginner-explanation';
import { IndicatorInterpretation } from '@/features/stock-detail/ai-signal/components/indicator-interpretation';
import { NewsImpact } from '@/features/stock-detail/ai-signal/components/news-impact';
import { WhatToWatch } from '@/features/stock-detail/ai-signal/components/what-to-watch';
import { useAiSignal } from '@/features/stock-detail/ai-signal/hooks/use-ai-signal';
import { AiAccuracyBadge } from '@/features/stock-detail/components/ai-accuracy-badge';
import { cn } from '@/lib/cn';

import type { AiSignal, AiSignalClass } from '@/types/ai-signal';
import type { TimeFrame } from '@/types/stock';

export function AiSignalPanel({ ticker, tf }: { ticker: string; tf: TimeFrame }) {
  const { user, isLoading: authLoading } = useAuth();

  if (!authLoading && !user) {
    return <AiSignalPreview />;
  }

  return <AiSignalContent ticker={ticker} tf={tf} />;
}

function AiSignalPreview() {
  return (
    <section
      aria-label="AI 참고 분석 미리보기"
      className="card relative overflow-hidden p-5"
    >
      <div className="mb-3">
        <h2 className="text-sm font-semibold text-fg">AI 참고 분석</h2>
      </div>
      <div className="select-none blur-sm" aria-hidden="true">
        <div className="rounded-xl bg-bg-muted p-3">
          <div className="flex items-center justify-between">
            <span className="rounded-md bg-bg-muted px-2 py-0.5 text-xs font-semibold text-fg-muted">시그널</span>
            <span className="text-xs text-fg-muted">신뢰도 --%</span>
          </div>
          <div className="mt-2 h-1.5 w-full rounded-full bg-bg-muted">
            <div className="h-full w-3/5 rounded-full bg-fg-muted" />
          </div>
        </div>
        <p className="mt-3 text-sm text-fg-secondary">
          AI가 시장 데이터, 기술 지표, 뉴스를 종합 분석하여 생성한 참고 시그널입니다.
        </p>
      </div>
      <div className="absolute inset-0 flex items-center justify-center bg-bg-surface/80 backdrop-blur-sm">
        <div className="text-center">
          <p className="text-sm font-medium text-fg">
            AI 분석 시그널은 로그인 후 이용할 수 있습니다
          </p>
          <p className="mt-1 text-xs text-fg-muted">
            매수/매도/중립 시그널과 근거를 AI가 분석해 드립니다
          </p>
          <Link
            href="/auth/login"
            className="mt-3 inline-block rounded-xl bg-primary px-5 py-2 text-sm font-medium text-primary-fg transition-colors hover:bg-primary-hover"
          >
            로그인하기
          </Link>
        </div>
      </div>
    </section>
  );
}

function AiSignalContent({ ticker, tf }: { ticker: string; tf: TimeFrame }) {
  const { data, isLoading, error } = useAiSignal(ticker, tf);

  if (isLoading) {
    return (
      <PanelShell>
        AI 분석 생성 중… 시장 데이터와 뉴스를 종합 분석하고 있어요 (최소 10초 소요)
      </PanelShell>
    );
  }
  if (error || !data) {
    return (
      <PanelShell>
        <span className="text-danger">
          AI 분석을 불러올 수 없습니다. 잠시 후 다시 시도해주세요.
        </span>
      </PanelShell>
    );
  }

  return (
    <section aria-label="AI 참고 분석" className="card brand-glow p-5">
      <div className="mb-3 flex items-center justify-between">
        <h2 className="text-sm font-semibold text-fg">AI 참고 분석</h2>
        <TimeframeBadge timeframe={data.timeframe} />
      </div>

      {data.fallback ? (
        <div className="mb-3 rounded-xl border border-amber-200 bg-amber-50 p-2 text-xs text-amber-800 dark:border-amber-900/40 dark:bg-amber-950/40 dark:text-amber-200">
          일시적으로 AI 분석이 제한되어 중립 관점으로 제공됩니다.
        </div>
      ) : null}

      <SignalHero signal={data.signal} confidence={data.confidence} />

      <p className="mt-3 text-sm text-fg-secondary">{data.summaryKo}</p>

      <div className="mt-4 flex flex-col gap-3">
        <BeginnerExplanation text={data.beginnerExplanation} />
        <IndicatorInterpretation items={data.indicatorInterpretation} />
        <NewsImpact items={data.newsImpact} generatedAt={data.generatedAt} />
        <WhatToWatch items={data.whatToWatch} />

        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <List title="근거" items={data.rationale} tone="neutral" />
          <List title="리스크" items={data.risks} tone="warning" />
        </div>
      </div>

      <p className="mt-4 text-xs text-fg-muted">{data.disclaimer}</p>
      <p className="mt-1 text-[11px] text-fg-muted">
        모델: {data.modelName} · 생성: {new Date(data.generatedAt).toLocaleString('ko-KR')}
      </p>

      <div className="mt-4 border-t border-border pt-3">
        <AiAccuracyBadge window={30} />
      </div>
    </section>
  );
}

function PanelShell({ children }: { children: React.ReactNode }) {
  return (
    <section className="card p-5 text-sm text-fg-muted">{children}</section>
  );
}

function TimeframeBadge({ timeframe }: { timeframe: AiSignal['timeframe'] }) {
  const label =
    timeframe === 'SHORT' ? '단기' : timeframe === 'MID' ? '중기' : '장기';
  return (
    <span className="rounded-md bg-bg-muted px-2 py-0.5 text-[10px] font-medium text-fg-secondary">
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
    <div className="rounded-xl bg-bg-muted p-4">
      <div className="flex items-center justify-between">
        <span className={cn('rounded-md px-2.5 py-1 text-xs font-bold', badgeCls)}>
          {label}
        </span>
        <span className="text-sm font-medium tabular-nums text-fg-secondary">
          신뢰도 <span className="text-fg">{pct}%</span>
        </span>
      </div>
      <div className="mt-3 h-2 w-full overflow-hidden rounded-full bg-bg-surface">
        <div
          className={cn('h-full rounded-full transition-all duration-500', barCls)}
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
        barCls: 'bg-emerald-500',
        badgeCls: 'bg-emerald-500/15 text-success',
      };
    case 'BUY':
      return {
        label: '긍정',
        barCls: 'bg-emerald-400',
        badgeCls: 'bg-emerald-500/10 text-success',
      };
    case 'NEUTRAL':
      return {
        label: '중립',
        barCls: 'bg-zinc-400 dark:bg-zinc-500',
        badgeCls: 'bg-bg-surface text-fg-secondary',
      };
    case 'SELL':
      return {
        label: '부정',
        barCls: 'bg-red-400',
        badgeCls: 'bg-red-500/10 text-danger',
      };
    case 'STRONG_SELL':
      return {
        label: '강한 부정',
        barCls: 'bg-red-500',
        badgeCls: 'bg-red-500/15 text-danger',
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
  const dotCls = tone === 'warning' ? 'bg-amber-400' : 'bg-primary/40';
  return (
    <div className="rounded-xl bg-bg-muted p-3">
      <h3 className="mb-2 text-xs font-semibold text-fg-muted">{title}</h3>
      <ul className="flex flex-col gap-1.5">
        {items.map((text, idx) => (
          <li
            key={`${title}-${idx}`}
            className="flex items-start gap-2 text-xs text-fg-secondary"
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
