'use client';

import Link from 'next/link';
import { GaugeComponent } from 'react-gauge-component';

import { useAuth } from '@/features/auth/auth-provider';
import { useMarketRegime } from '@/features/market-dashboard/hooks/use-market-regime';
import { useMarketRegimeAi } from '@/features/market-dashboard/hooks/use-market-regime-ai';

import { InfoTooltip } from '@/components/ui/info-tooltip';
import { InlineLoading, PanelLoading } from '@/components/ui/panel-loading';

import type { MarketRegimeComposite, RegimeIndicator, SectorMomentum } from '@/types/market';

const PANEL_TOOLTIP =
  '밸류에이션·심리·매크로·추세 지표를 종합해 현재 시장 국면을 보여줍니다. 모든 수치는 참고용이며 매매 신호가 아닙니다.';

const COMPOSITE_TOOLTIP =
  '여러 지표를 종합한 시장 온도계입니다. 0(공포·저평가)에 가까울수록 위축, 100(과열·고평가)에 가까울수록 과열을 뜻합니다. 참고용입니다.';

const REGIME_TOOLTIPS: Record<string, string> = {
  buffett:
    '전체 주식 시가총액을 GDP로 나눈 값입니다. 높을수록 경제 규모 대비 고평가됐음을 뜻합니다. 기준: 100% 미만 저평가 · 100~140% 정상 · 140~180% 고평가 · 180% 초과 과열.',
  fearGreed:
    '투자 심리를 0(극도 공포)~100(극도 탐욕)으로 나타낸 CNN 지수입니다. 기준: 25 이하 극단적 공포 · 55~75 탐욕 · 75 이상 극단적 탐욕.',
  creditSpread:
    '투기등급(HY) 회사채와 국채의 금리 차이입니다. 벌어질수록 위험 회피가 강함을 뜻합니다. 기준: 3% 미만 안정 · 5% 초과 신용 스트레스.',
  vix: "S&P500 옵션 기반 변동성 지수('공포 지수')입니다. 기준: 15 미만 안정 · 20 초과 불안 심리. 단일 지표 해석은 주의가 필요합니다.",
  yieldCurve2y:
    '10년물−2년물 국채 금리차입니다. 기준: 0 미만(역전)은 과거 경기 침체에 선행한 사례가 있음 · 0.5%p 초과 정상.',
  yieldCurve3m:
    '10년물−3개월물 국채 금리차입니다. 기준: 0 미만(역전)이 침체 신호로 자주 인용됨 · 0.5%p 초과 정상.',
  unemployment:
    '미국 실업률입니다. 기준: 4% 안팎 완전고용 수준 · 5% 초과 경기 둔화. 절대값보다 상승 전환 여부가 중요합니다.',
  netLiquidity:
    'Fed 자산에서 재무부 계정·역레포를 뺀 시중 유동성입니다. 클수록 완화적이며, 게이지는 최근 2년 범위(저점~고점) 중 현재 위치를 나타냅니다.',
  sp500vs200ma:
    'S&P500이 200일 이동평균 대비 얼마나 위/아래인지입니다. 기준: +2% 초과 상승추세 · ±2% 이내 횡보 · -2% 미만 하락추세.',
};

/** 지표별 zone 단계 (반원 게이지 구간 색). 정의가 없으면 단색 게이지. */
const REGIME_SEGMENTS: Record<string, { zone: string; label: string }[]> = {
  buffett: [
    { zone: 'cheap', label: '저평가' },
    { zone: 'normal', label: '정상' },
    { zone: 'caution', label: '고평가' },
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
    { zone: 'neutral', label: '횡보' },
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
  caution: '다소 고평가',
};

function zoneTextColor(zone: string): string {
  switch (zone) {
    case 'caution':
      return 'text-amber-500';
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

function zoneBg(zone: string): string {
  switch (zone) {
    case 'caution':
      return 'bg-amber-500';
    case 'overheated':
    case 'greed':
    case 'elevated':
      return 'bg-red-500';
    case 'fear':
    case 'inverted':
    case 'downtrend':
    case 'cheap':
      return 'bg-blue-500';
    case 'calm':
    case 'uptrend':
    case 'low':
    case 'normal':
      return 'bg-emerald-500';
    default:
      return 'bg-zinc-500';
  }
}

export function MarketRegime() {
  const { data, isLoading } = useMarketRegime();

  if (isLoading) {
    return <PanelLoading title="시장 국면" text="시장 국면 지표를 불러오고 있어요" />;
  }
  if (!data) return null;

  const all: RegimeIndicator[] = [
    ...data.axes.valuation.indicators,
    ...data.axes.riskSentiment.indicators,
    ...data.axes.macro.indicators,
    ...data.axes.trendBreadth.indicators,
  ];
  // 공포·탐욕 지수는 헤드라인 CNN형 게이지로 분리, 나머지는 바 그리드로.
  const fearGreed = all.find((it) => it.key === 'fearGreed') ?? null;
  const indicators = all.filter((it) => it.key !== 'fearGreed');

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

      {fearGreed && <FearGreedGauge indicator={fearGreed} />}

      <div className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-3">
        {indicators.map((it) => (
          <IndicatorCard key={it.key} indicator={it} />
        ))}
      </div>

      <MomentumHeatmap title="섹터 모멘텀 (최근 3개월)" tooltip={SECTOR_TOOLTIP} items={data.sectors} />
      <MomentumHeatmap title="테마 모멘텀 (최근 3개월)" tooltip={THEME_TOOLTIP} items={data.themes} />

      <AiSection />

      <p className="mt-4 text-xs leading-relaxed text-fg-secondary">{data.disclaimer}</p>
    </section>
  );
}

/** 종합 국면 — 수평 그라디언트 바 + 흰 점 마커. */
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
  const isRange = key === 'netLiquidity';

  return (
    <div className="flex flex-col rounded-xl bg-bg-muted p-3 ring-1 ring-border">
      <div className="flex items-center gap-1 text-xs font-medium text-fg-secondary">
        <span className="truncate">{name}</span>
        {tooltip && <InfoTooltip text={tooltip} />}
      </div>

      <div className="mt-1.5 flex items-baseline gap-2">
        <span className="text-xl font-bold tabular-nums text-fg">
          {value !== null ? `${value}${unit ?? ''}` : '—'}
        </span>
        <span className={`text-[11px] font-semibold ${zoneTextColor(zone)}`}>{ZONE_KO[zone] ?? zone}</span>
      </div>
      {position !== null && (
        <RegimeBar position={position} segments={isRange ? undefined : segments} current={zone} range={isRange} />
      )}

      {note && <div className="mt-1.5 text-[11px] leading-snug text-fg-secondary">{note}</div>}
    </div>
  );
}

/**
 * 수평 존 바 — 레벨/발산형 지표용. 존 밴드(활성 강조) + position 흰 점 마커.
 * range=true면 단색 트랙 + 저점~고점 라벨(순유동성 등 절대값 → 범위 내 위치).
 */
function RegimeBar({
  position,
  segments,
  current,
  range,
}: {
  position: number;
  segments?: { zone: string; label: string }[];
  current: string;
  range?: boolean;
}) {
  const pos = Math.max(0, Math.min(100, position));
  const segs = !range && segments && segments.length > 0 ? segments : null;
  // 바 양 끝 방향 범례 — 좌(낮은 쪽) / 우(높은 쪽). 라벨이 방향을 안내해 오해 방지.
  const endLabels: [string, string] | null = range
    ? ['2년 저점', '고점']
    : segs
      ? [segs[0].label, segs[segs.length - 1].label]
      : null;

  return (
    <div className="mt-2">
      <div className="relative h-3 w-full">
        {segs ? (
          <div className="absolute inset-0 flex gap-0.5 overflow-hidden rounded-full">
            {segs.map((seg) => (
              <div
                key={seg.zone}
                className={`flex-1 ${zoneBg(seg.zone)} ${seg.zone === current ? 'opacity-90' : 'opacity-20'}`}
              />
            ))}
          </div>
        ) : (
          <div className="absolute inset-0 rounded-full bg-gradient-to-r from-blue-500/30 via-bg-skeleton to-emerald-500/40" />
        )}
        <div
          className="absolute top-1/2 h-3.5 w-3.5 -translate-y-1/2 rounded-full border-2 border-bg-surface bg-fg shadow"
          style={{ left: `calc(${pos}% - 7px)` }}
        />
      </div>
      {endLabels && (
        <div className="mt-1 flex justify-between text-[10px] font-medium text-fg-muted">
          <span>{endLabels[0]}</span>
          <span>{endLabels[1]}</span>
        </div>
      )}
    </div>
  );
}

/** 공포·탐욕 5개 존 (CNN 기준). 게이지 색과 일치. */
const FG_ZONES: { label: string; color: string; max: number }[] = [
  { label: '극도공포', color: '#1d4ed8', max: 25 },
  { label: '공포', color: '#3b82f6', max: 45 },
  { label: '중립', color: '#71717a', max: 55 },
  { label: '탐욕', color: '#f87171', max: 75 },
  { label: '극도탐욕', color: '#dc2626', max: 100 },
];

/**
 * 공포·탐욕 지수 — CNN Fear&Greed 스타일 반원 게이지 (react-gauge-component).
 * 5개 존 색 호 + 눈금 + 바늘. 영역명은 아래 범례(현재 영역 강조)로 표기.
 */
function FearGreedGauge({ indicator }: { indicator: RegimeIndicator }) {
  const { value, note } = indicator;
  const v = value ?? 50;
  const activeZone = FG_ZONES.find((z) => v <= z.max) ?? FG_ZONES[FG_ZONES.length - 1];

  return (
    <div className="mt-3 rounded-xl bg-bg-muted p-4 ring-1 ring-border">
      <div className="flex items-center gap-1 text-xs font-medium text-fg-secondary">
        공포·탐욕 지수
        <InfoTooltip text={REGIME_TOOLTIPS.fearGreed} />
      </div>

      <div className="mx-auto max-w-[300px]">
        <GaugeComponent
          type="semicircle"
          value={v}
          minValue={0}
          maxValue={100}
          marginInPercent={{ top: 0.08, bottom: 0.24, left: 0.07, right: 0.07 }}
          arc={{
            cornerRadius: 3,
            padding: 0.008,
            width: 0.22,
            subArcs: FG_ZONES.map((z) => ({ limit: z.max, color: z.color })),
          }}
          pointer={{
            type: 'needle',
            color: 'var(--fg)',
            baseColor: 'var(--fg)',
            length: 0.7,
            width: 10,
            elastic: true,
          }}
          labels={{
            valueLabel: {
              formatTextValue: (val: number) => `${Math.round(val)}`,
              offsetY: 42,
              style: { fontSize: '30px', fontWeight: '800', fill: 'var(--fg)', textShadow: 'none' },
            },
            tickLabels: {
              type: 'outer',
              hideMinMax: true,
              ticks: [{ value: 0 }, { value: 25 }, { value: 50 }, { value: 75 }, { value: 100 }],
              defaultTickValueConfig: {
                formatTextValue: (val: number) => `${val}`,
                style: { fontSize: '10px', fill: 'var(--fg-muted)' },
              },
              defaultTickLineConfig: { color: 'var(--fg-muted)' },
            },
          }}
        />
      </div>

      {/* 영역 범례 — 현재 영역 강조 */}
      <div className="mt-1 flex flex-wrap justify-center gap-x-3 gap-y-1">
        {FG_ZONES.map((z) => {
          const active = z.label === activeZone.label;
          return (
            <span
              key={z.label}
              className={`flex items-center gap-1 text-[10px] ${active ? 'font-bold text-fg' : 'text-fg-muted'}`}
            >
              <span
                className="inline-block h-2 w-2 rounded-full"
                style={{ backgroundColor: z.color, opacity: active ? 1 : 0.5 }}
              />
              {z.label}
            </span>
          );
        })}
      </div>

      {note && <p className="mt-1.5 text-center text-[11px] leading-snug text-fg-secondary">{note}</p>}
    </div>
  );
}

const SECTOR_TOOLTIP =
  '최근 3개월간 각 섹터 ETF의 누적 수익률입니다. 양수는 강세, 음수는 약세를 뜻하며, 자금이 어느 섹터로 이동하는지(섹터 로테이션) 흐름을 보여줍니다. 참고용입니다.';

const THEME_TOOLTIP =
  '반도체·AI·클라우드·바이오 등 세부 테마 ETF의 최근 3개월 누적 수익률입니다. 섹터보다 잘게 나눠 어느 테마로 자금이 쏠리는지 봅니다. 색이 진할수록 강도가 큽니다. 참고용입니다.';

/**
 * 분기 모멘텀 히트맵 — Finviz 스타일 타일 그리드 (섹터/테마 공용).
 * 수익률 강도에 따라 녹(강세)↔빨(약세) 색 농도. 강세→약세 정렬(BE).
 */
function MomentumHeatmap({ title, tooltip, items }: { title: string; tooltip: string; items: SectorMomentum[] }) {
  if (!items || items.length === 0) return null;

  return (
    <div className="mt-4 rounded-xl bg-bg-muted p-4 ring-1 ring-border">
      <div className="mb-3 flex items-center gap-1 text-xs font-medium text-fg-secondary">
        {title}
        <InfoTooltip text={tooltip} />
      </div>
      <div className="grid grid-cols-2 gap-1.5 sm:grid-cols-3 lg:grid-cols-4">
        {items.map((s) => {
          const pct = s.returnPct;
          const positive = pct >= 0;
          // ±12% 기준으로 색 농도(0~1) 산출. outlier(기술 +32%)는 최대 농도로 clamp.
          const t = Math.min(Math.abs(pct) / 12, 1);
          const alpha = (0.15 + 0.6 * t).toFixed(2);
          const bg = positive ? `rgba(16,185,129,${alpha})` : `rgba(239,68,68,${alpha})`;
          const strong = t > 0.45; // 진한 타일은 흰 글씨, 연한 타일은 테마 글씨
          return (
            <div
              key={s.sector}
              className={`flex flex-col gap-0.5 rounded-lg px-2.5 py-2 ${strong ? 'text-white' : 'text-fg'}`}
              style={{ backgroundColor: bg }}
            >
              <span className="truncate text-[11px] font-medium opacity-90">{s.sectorKo}</span>
              <span className="text-sm font-bold tabular-nums">
                {positive ? '+' : ''}
                {pct}%
              </span>
            </div>
          );
        })}
      </div>
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
      <div className="space-y-2.5 text-sm leading-relaxed text-fg-secondary">
        {data.aiSummary.split(/\n\n+/).map((para, i) => (
          <p key={i}>{renderEmphasis(para)}</p>
        ))}
      </div>
    </div>
  );
}

/** AI 해석의 **강조** 표기를 굵은 글씨로 변환 (전체 마크다운 파서 없이 **...**만 처리). */
function renderEmphasis(text: string) {
  return text.split(/(\*\*[^*]+\*\*)/g).map((part, i) =>
    part.startsWith('**') && part.endsWith('**') ? (
      <strong key={i} className="font-semibold text-fg">
        {part.slice(2, -2)}
      </strong>
    ) : (
      <span key={i}>{part}</span>
    ),
  );
}
