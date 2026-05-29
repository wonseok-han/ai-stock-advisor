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
    'Fed 자산에서 재무부 계정·역레포를 뺀 시중 유동성입니다. 클수록 완화적이며, 게이지는 최근 2년 범위(저점~고점) 중 현재 위치를 나타냅니다.',
  sp500vs200ma:
    'S&P500이 200일 이동평균 대비 얼마나 위/아래인지입니다. 기준: 0 이상 장기 상승추세 · 0 미만 하락추세.',
};

/** 지표별 zone 단계 (반원 게이지 구간 색). 정의가 없으면 단색 게이지. */
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
    { zone: 'downtrend', label: '하락' },
    { zone: 'uptrend', label: '상승' },
  ],
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
  low: '낮음',
  elevated: '높음',
};

function zoneTextColor(zone: string): string {
  switch (zone) {
    case 'overheated':
    case 'greed':
    case 'elevated':
      return 'text-danger';
    case 'fear':
    case 'inverted':
    case 'downtrend':
    case 'cheap':
      return 'text-blue-400';
    case 'calm':
    case 'uptrend':
    case 'low':
    case 'normal':
      return 'text-success';
    default:
      return 'text-fg-secondary';
  }
}

function zoneStroke(zone: string): string {
  switch (zone) {
    case 'overheated':
    case 'greed':
    case 'elevated':
      return 'stroke-red-500';
    case 'fear':
    case 'inverted':
    case 'downtrend':
    case 'cheap':
      return 'stroke-blue-500';
    case 'calm':
    case 'uptrend':
    case 'low':
    case 'normal':
      return 'stroke-emerald-500';
    default:
      return 'stroke-zinc-500';
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
  const { key, name, value, unit, zone, note, position } = indicator;
  const tooltip = REGIME_TOOLTIPS[key];
  const segments = REGIME_SEGMENTS[key];
  return (
    <div className="flex flex-col rounded-xl bg-bg-muted p-3 ring-1 ring-border">
      <div className="flex items-center gap-1 text-xs font-medium text-fg-secondary">
        <span className="truncate">{name}</span>
        {tooltip && <InfoTooltip text={tooltip} />}
      </div>

      {position !== null ? (
        <RegimeGauge
          position={position}
          segments={segments}
          current={zone}
          value={value}
          unit={unit}
        />
      ) : (
        <div className="mt-1 text-xl font-bold tabular-nums text-fg">
          {value !== null ? `${value}${unit ?? ''}` : '—'}
        </div>
      )}

      <div className={`text-center text-[11px] font-semibold ${zoneTextColor(zone)}`}>
        {ZONE_KO[zone] ?? zone}
      </div>
      {note && <div className="mt-1 text-[11px] leading-snug text-fg-secondary">{note}</div>}
    </div>
  );
}

function polar(cx: number, cy: number, r: number, deg: number) {
  const rad = (deg * Math.PI) / 180;
  return { x: cx + r * Math.cos(rad), y: cy - r * Math.sin(rad) };
}

function arc(cx: number, cy: number, r: number, startDeg: number, endDeg: number) {
  const s = polar(cx, cy, r, startDeg);
  const e = polar(cx, cy, r, endDeg);
  // 왼(180°)→오(0°) 위쪽 반원: sweep=1
  return `M ${s.x.toFixed(2)} ${s.y.toFixed(2)} A ${r} ${r} 0 0 1 ${e.x.toFixed(2)} ${e.y.toFixed(2)}`;
}

/** 미니 반원 게이지 — zone 구간 색 호 + position 바늘 + 중앙 값. */
function RegimeGauge({
  position,
  segments,
  current,
  value,
  unit,
}: {
  position: number;
  segments?: { zone: string; label: string }[];
  current: string;
  value: number | null;
  unit: string | null;
}) {
  const cx = 50;
  const cy = 47;
  const r = 38;
  const pos = Math.max(0, Math.min(100, position));
  const needleAngle = 180 - (pos / 100) * 180;
  const tip = polar(cx, cy, r - 5, needleAngle);

  const segs = segments ?? [{ zone: current, label: '' }];
  const n = segs.length;

  return (
    <svg viewBox="0 0 100 56" className="mt-1 w-full" role="img" aria-label={`${value ?? ''}${unit ?? ''}`}>
      {/* 배경 호 */}
      <path d={arc(cx, cy, r, 180, 0)} fill="none" strokeWidth="6" className="stroke-bg-surface" strokeLinecap="round" />
      {/* zone 구간 호 */}
      {segs.map((seg, i) => {
        const a0 = 180 - i * (180 / n);
        const a1 = 180 - (i + 1) * (180 / n);
        const active = seg.zone === current;
        return (
          <path
            key={seg.zone}
            d={arc(cx, cy, r, a0, a1)}
            fill="none"
            strokeWidth="6"
            strokeLinecap="round"
            className={`${zoneStroke(seg.zone)} ${active ? 'opacity-100' : 'opacity-25'}`}
          />
        );
      })}
      {/* 바늘 */}
      <line x1={cx} y1={cy} x2={tip.x.toFixed(2)} y2={tip.y.toFixed(2)} className="stroke-fg" strokeWidth="2" strokeLinecap="round" />
      <circle cx={cx} cy={cy} r="3" className="fill-fg" />
      {/* 값 */}
      <text x={cx} y={cy - 11} textAnchor="middle" className="fill-fg text-[13px] font-bold">
        {value !== null ? `${value}${unit ?? ''}` : '—'}
      </text>
    </svg>
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
      <p className="whitespace-pre-line text-sm leading-relaxed text-fg-secondary">{data.aiSummary}</p>
    </div>
  );
}
