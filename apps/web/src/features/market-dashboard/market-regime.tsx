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
    '전체 주식 시가총액을 GDP로 나눈 값입니다. 높을수록 경제 규모 대비 고평가됐음을 뜻합니다. 기준: 100% 미만 저평가 · 150% 초과 고평가 · 200% 초과 역사적 과열.',
  fearGreed:
    '투자 심리를 0(극도 공포)~100(극도 탐욕)으로 나타낸 CNN 지수입니다. 기준: 25 이하 극단적 공포 · 55~75 탐욕 · 75 이상 극단적 탐욕.',
  creditSpread:
    '투기등급(HY) 회사채와 국채의 금리 차이입니다. 벌어질수록 위험 회피가 강함을 뜻합니다. 기준: 3% 미만 안정 · 5% 초과 신용 스트레스.',
  vix: "S&P500 옵션 기반 변동성 지수('공포 지수')입니다. 기준: 15 미만 안정 · 25 초과 불안 심리. 단일 지표 해석은 주의가 필요합니다.",
  yieldCurve2y:
    '10년물−2년물 국채 금리차입니다. 기준: 0 미만(역전)은 과거 경기 침체에 선행한 사례가 있음 · 0.5%p 초과 정상.',
  yieldCurve3m:
    '10년물−3개월물 국채 금리차입니다. 기준: 0 미만(역전)이 침체 신호로 자주 인용됨 · 0.5%p 초과 정상.',
  unemployment:
    '미국 실업률입니다. 기준: 4% 안팎 완전고용 수준 · 5% 초과 경기 둔화. 절대값보다 상승 전환 여부가 중요합니다.',
  netLiquidity:
    'Fed 자산에서 재무부 계정·역레포를 뺀 시중 유동성입니다. 클수록 완화적이며, 절대 기준보다 증감 추세가 중요합니다.',
  sp500vs200ma:
    'S&P500이 200일 이동평균 대비 얼마나 위/아래인지입니다. 기준: 0 이상 장기 상승추세 · 0 미만 하락추세.',
};

/** 지표별 zone 단계 (세그먼트 막대). 정의가 없으면 값만 표시. */
const REGIME_SEGMENTS: Record<string, { zone: string; label: string }[]> = {
  buffett: [
    { zone: 'cheap', label: '저평가' },
    { zone: 'normal', label: '정상' },
    { zone: 'overheated', label: '과열' },
  ],
  fearGreed: [
    { zone: 'fear', label: '공포' },
    { zone: 'neutral', label: '중립' },
    { zone: 'greed', label: '탐욕' },
  ],
  creditSpread: [
    { zone: 'calm', label: '안정' },
    { zone: 'normal', label: '정상' },
    { zone: 'fear', label: '위험' },
  ],
  vix: [
    { zone: 'calm', label: '안정' },
    { zone: 'normal', label: '정상' },
    { zone: 'fear', label: '불안' },
  ],
  yieldCurve2y: [
    { zone: 'inverted', label: '역전' },
    { zone: 'neutral', label: '평탄' },
    { zone: 'normal', label: '정상' },
  ],
  yieldCurve3m: [
    { zone: 'inverted', label: '역전' },
    { zone: 'neutral', label: '평탄' },
    { zone: 'normal', label: '정상' },
  ],
  unemployment: [
    { zone: 'low', label: '낮음' },
    { zone: 'normal', label: '보통' },
    { zone: 'elevated', label: '높음' },
  ],
  sp500vs200ma: [
    { zone: 'downtrend', label: '하락추세' },
    { zone: 'uptrend', label: '상승추세' },
  ],
};

/** 활성 세그먼트(현재 구간) 강조 색. */
function activeZoneColor(zone: string): string {
  switch (zone) {
    case 'overheated':
    case 'greed':
    case 'elevated':
      return 'bg-red-500/15 text-danger ring-red-500/30';
    case 'fear':
    case 'inverted':
    case 'downtrend':
    case 'cheap':
      return 'bg-blue-500/15 text-blue-400 ring-blue-500/30';
    case 'calm':
    case 'uptrend':
    case 'low':
      return 'bg-emerald-500/15 text-success ring-emerald-500/30';
    default:
      return 'bg-fg/10 text-fg ring-border';
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
        <span className="rounded-md bg-bg-muted px-1.5 py-0.5 text-[10px] text-fg-secondary ring-1 ring-border">
          참고용
        </span>
      </div>

      {data.composite && <CompositeGauge composite={data.composite} />}

      <div className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-3">
        {indicators.map((it) => (
          <IndicatorCard key={it.key} indicator={it} />
        ))}
      </div>

      <AiSection />

      <p className="mt-4 text-xs leading-relaxed text-fg-secondary">{data.disclaimer}</p>
    </section>
  );
}

function CompositeGauge({ composite }: { composite: MarketRegimeComposite }) {
  const score = Math.max(0, Math.min(100, composite.score));
  return (
    <div className="rounded-xl bg-bg-muted p-4 ring-1 ring-border">
      <div className="flex items-center justify-between">
        <span className="flex items-center gap-1 text-xs font-medium text-fg-secondary">
          종합 국면 (공포 ↔ 과열)
          <InfoTooltip text={COMPOSITE_TOOLTIP} />
        </span>
        <span className="text-sm font-bold text-fg">
          {composite.labelKo} <span className="text-fg-secondary">· {score}/100</span>
        </span>
      </div>
      <div className="relative mt-3 h-2.5 w-full rounded-full bg-gradient-to-r from-blue-500/60 via-bg-skeleton to-red-500/60">
        <div
          className="absolute top-1/2 h-4 w-4 -translate-y-1/2 rounded-full border-2 border-bg-surface bg-fg shadow-md"
          style={{ left: `calc(${score}% - 8px)` }}
        />
      </div>
      <div className="mt-1.5 flex justify-between text-[10px] font-medium text-fg-secondary">
        <span>공포·저평가</span>
        <span>과열·고평가</span>
      </div>
    </div>
  );
}

function IndicatorCard({ indicator }: { indicator: RegimeIndicator }) {
  const { key, name, value, unit, zone, note } = indicator;
  const tooltip = REGIME_TOOLTIPS[key];
  const segments = REGIME_SEGMENTS[key];
  return (
    <div className="rounded-xl bg-bg-muted p-3 ring-1 ring-border">
      <div className="flex items-center gap-1 text-xs font-medium text-fg-secondary">
        <span className="truncate">{name}</span>
        {tooltip && <InfoTooltip text={tooltip} />}
      </div>
      <div className="mt-1 text-xl font-bold tabular-nums text-fg">
        {value !== null ? `${value}${unit ?? ''}` : '—'}
      </div>
      {segments ? (
        <SegmentBar segments={segments} current={zone} />
      ) : (
        zone !== 'neutral' && (
          <span className={`mt-2 inline-block rounded-md px-2 py-0.5 text-[10px] font-semibold ring-1 ${activeZoneColor(zone)}`}>
            {zone}
          </span>
        )
      )}
      {note && <div className="mt-1.5 text-[11px] leading-snug text-fg-secondary">{note}</div>}
    </div>
  );
}

function SegmentBar({ segments, current }: { segments: { zone: string; label: string }[]; current: string }) {
  return (
    <div className="mt-2 flex gap-1">
      {segments.map((seg) => {
        const active = seg.zone === current;
        return (
          <div
            key={seg.zone}
            className={`flex-1 rounded-md py-1 text-center text-[10px] ring-1 transition-colors ${
              active
                ? `font-bold ${activeZoneColor(seg.zone)}`
                : 'bg-transparent font-medium text-fg-secondary ring-border'
            }`}
          >
            {seg.label}
          </div>
        );
      })}
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
      <div className="mt-4 rounded-xl bg-bg-muted p-4 ring-1 ring-border">
        <InlineLoading text="AI가 시장 국면을 해석하고 있어요" />
      </div>
    );
  }
  if (!data?.aiSummary) return null;

  return (
    <div className="mt-4 rounded-xl bg-bg-muted p-4 ring-1 ring-border">
      <div className="mb-1.5 flex items-center gap-2">
        <span className="text-xs font-semibold text-fg">AI 해석</span>
        <span className="rounded-md bg-bg-surface px-1.5 py-0.5 text-[10px] text-fg-secondary ring-1 ring-border">참고</span>
      </div>
      <p className="text-sm leading-relaxed text-fg-secondary">{data.aiSummary}</p>
    </div>
  );
}
