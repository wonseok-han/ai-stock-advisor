'use client';

import Link from 'next/link';

import { useAuth } from '@/features/auth/auth-provider';
import { useMarketRegime } from '@/features/market-dashboard/hooks/use-market-regime';
import { useMarketRegimeAi } from '@/features/market-dashboard/hooks/use-market-regime-ai';

import { InfoTooltip } from '@/components/ui/info-tooltip';
import { InlineLoading, PanelLoading } from '@/components/ui/panel-loading';

import type { MarketRegimeComposite, RegimeIndicator } from '@/types/market';

const PANEL_TOOLTIP =
  '밸류에이션·심리·매크로·추세 지표를 종합해 현재 시장 국면을 보여줍니다. 모든 수치는 참고용이며 매매 신호가 아닙니다.';

const COMPOSITE_TOOLTIP =
  '여러 지표를 종합한 시장 온도계입니다. 0(공포·저평가)에 가까울수록 위축, 100(과열·고평가)에 가까울수록 과열을 뜻합니다. 참고용입니다.';

const REGIME_TOOLTIPS: Record<string, string> = {
  buffett:
    '전체 주식 시가총액을 GDP로 나눈 값입니다. 높을수록 경제 규모 대비 주식시장이 고평가됐음을 시사합니다.',
  fearGreed: '투자 심리를 0(극도 공포)~100(극도 탐욕)으로 나타낸 CNN 지수입니다.',
  creditSpread:
    '투기등급(HY) 회사채와 국채의 금리 차이입니다. 벌어질수록 위험 회피 심리가 강함을 뜻합니다.',
  vix: "S&P500 옵션 기반 변동성 지수입니다. '공포 지수'로 불리지만 단일 지표 해석은 주의가 필요합니다.",
  yieldCurve2y:
    '10년물과 2년물 국채 금리차입니다. 마이너스(역전)는 과거 경기 침체에 선행한 사례가 있습니다.',
  yieldCurve3m: '10년물과 3개월물 국채 금리차입니다. 침체 신호로 자주 인용됩니다.',
  unemployment: '미국 실업률입니다. 낮으면 경기 호조이나, 상승 전환은 둔화 신호일 수 있습니다.',
  netLiquidity:
    'Fed 자산에서 재무부 계정과 역레포를 뺀 시중 유동성입니다. 클수록 완화적입니다.',
  sp500vs200ma:
    'S&P500이 200일 이동평균 대비 얼마나 위/아래인지를 나타냅니다. 장기 추세 판단에 쓰입니다.',
};

const ZONE_KO: Record<string, string> = {
  cheap: '저평가',
  calm: '안정',
  normal: '정상',
  neutral: '중립',
  greed: '탐욕',
  overheated: '과열',
  fear: '공포',
  inverted: '역전',
  uptrend: '상승추세',
  downtrend: '하락추세',
};

function zoneColor(zone: string): string {
  switch (zone) {
    case 'overheated':
    case 'greed':
      return 'bg-red-500/10 text-danger';
    case 'fear':
    case 'inverted':
    case 'downtrend':
    case 'cheap':
      return 'bg-blue-500/10 text-blue-400';
    case 'calm':
    case 'uptrend':
      return 'bg-emerald-500/10 text-success';
    default:
      return 'bg-bg-surface text-fg-muted';
  }
}

export function MarketRegime() {
  const { data, isLoading } = useMarketRegime();

  if (isLoading) {
    return <PanelLoading title="시장 국면" text="시장 국면 지표를 불러오고 있어요" />;
  }
  if (!data) return null;

  const indicators: RegimeIndicator[] = [
    ...data.axes.valuation.indicators,
    ...data.axes.riskSentiment.indicators,
    ...data.axes.macro.indicators,
    ...data.axes.trendBreadth.indicators,
  ];

  return (
    <section aria-label="시장 국면" className="card p-5">
      <div className="mb-4 flex items-center justify-between">
        <div className="flex items-center gap-1.5">
          <h2 className="text-sm font-semibold text-fg">시장 국면</h2>
          <InfoTooltip text={PANEL_TOOLTIP} />
        </div>
        <span className="rounded-md bg-bg-muted px-1.5 py-0.5 text-[10px] text-fg-muted">참고용</span>
      </div>

      {data.composite && <CompositeGauge composite={data.composite} />}

      <div className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-3">
        {indicators.map((it) => (
          <IndicatorCard key={it.key} indicator={it} />
        ))}
      </div>

      <AiSection />

      <p className="mt-4 text-xs leading-relaxed text-fg-muted">{data.disclaimer}</p>
    </section>
  );
}

function CompositeGauge({ composite }: { composite: MarketRegimeComposite }) {
  const score = Math.max(0, Math.min(100, composite.score));
  return (
    <div className="rounded-xl bg-bg-muted p-4">
      <div className="flex items-center justify-between">
        <span className="flex items-center gap-1 text-xs font-medium text-fg-muted">
          종합 국면 (공포 ↔ 과열)
          <InfoTooltip text={COMPOSITE_TOOLTIP} />
        </span>
        <span className="text-sm font-bold text-fg">
          {composite.labelKo} <span className="text-fg-muted">· {score}/100</span>
        </span>
      </div>
      <div className="relative mt-3 h-2 w-full rounded-full bg-gradient-to-r from-blue-500/40 via-bg-surface to-red-500/40">
        <div
          className="absolute top-1/2 h-3.5 w-3.5 -translate-y-1/2 rounded-full border-2 border-bg-surface bg-fg"
          style={{ left: `calc(${score}% - 7px)` }}
        />
      </div>
      <div className="mt-1.5 flex justify-between text-[10px] text-fg-muted">
        <span>공포·저평가</span>
        <span>과열·고평가</span>
      </div>
    </div>
  );
}

function IndicatorCard({ indicator }: { indicator: RegimeIndicator }) {
  const { key, name, value, unit, zone, note } = indicator;
  const tooltip = REGIME_TOOLTIPS[key];
  return (
    <div className="rounded-xl bg-bg-muted p-3">
      <div className="flex items-center justify-between gap-2">
        <span className="flex items-center gap-1 truncate text-xs font-medium text-fg-muted">
          <span className="truncate">{name}</span>
          {tooltip && <InfoTooltip text={tooltip} />}
        </span>
        <span className={`shrink-0 rounded-full px-2 py-0.5 text-[10px] font-semibold ${zoneColor(zone)}`}>
          {ZONE_KO[zone] ?? zone}
        </span>
      </div>
      <div className="mt-1.5 text-lg font-bold tabular-nums text-fg">
        {value !== null ? `${value}${unit ?? ''}` : '—'}
      </div>
      {note && <div className="mt-0.5 text-[11px] leading-tight text-fg-muted">{note}</div>}
    </div>
  );
}

function AiSection() {
  const { user, isLoading: authLoading } = useAuth();
  const { data, isLoading } = useMarketRegimeAi(!!user);

  if (authLoading) return null;

  if (!user) {
    return (
      <div className="mt-4 rounded-xl border border-border bg-bg-muted/50 p-4 text-center">
        <p className="text-sm font-medium text-fg">AI 국면 해석은 로그인 후 이용할 수 있습니다</p>
        <Link
          href="/auth/login"
          className="mt-2 inline-block rounded-xl bg-primary px-4 py-2 text-sm font-medium text-primary-fg hover:bg-primary-hover"
        >
          로그인하기
        </Link>
      </div>
    );
  }

  if (isLoading) {
    return (
      <div className="mt-4 rounded-xl bg-bg-muted p-4">
        <InlineLoading text="AI가 시장 국면을 해석하고 있어요" />
      </div>
    );
  }
  if (!data?.aiSummary) return null;

  return (
    <div className="mt-4 rounded-xl bg-bg-muted p-4">
      <div className="mb-1 flex items-center gap-2">
        <span className="text-xs font-semibold text-fg">AI 해석</span>
        <span className="rounded-md bg-bg-surface px-1.5 py-0.5 text-[10px] text-fg-muted">참고</span>
      </div>
      <p className="text-sm leading-relaxed text-fg-secondary">{data.aiSummary}</p>
    </div>
  );
}
