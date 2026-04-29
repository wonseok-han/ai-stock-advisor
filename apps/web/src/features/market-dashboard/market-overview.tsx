'use client';

import { useCallback, useEffect, useRef, useState } from 'react';

import { PanelLoading } from '@/components/ui/panel-loading';
import { InfoTooltip } from '@/components/ui/info-tooltip';
import { useMarketOverview } from '@/features/market-dashboard/hooks/use-market-overview';
import { cn } from '@/lib/cn';
import { formatPercentChange, formatSignedNumber } from '@/lib/format/percent';

import type { MarketIndex } from '@/types/market';

const HERO_ROTATE_MS = 6000;

const INDEX_TOOLTIPS: Record<string, string> = {
  'S&P 500': '미국 대형주 500개의 시가총액 가중 지수. 미국 주식 시장 전체의 건강 상태를 나타내는 대표 지표입니다.',
  'Nasdaq': 'IT·바이오 등 기술주 중심의 지수. 성장주 비중이 높아 기술 섹터 흐름을 파악할 수 있습니다.',
  'Dow Jones': '미국 우량 대형주 30개로 구성된 전통 지수. 가격 가중 방식으로 고가 종목의 영향이 큽니다.',
  'Russell 2000': '미국 소형주 2,000개로 구성된 지수. 내수 경기와 중소기업 체감 경기를 반영합니다.',
  'S&P 500 선물': 'S&P 500 E-mini 선물. 거의 24시간 거래되어 장외 시간에도 시장 방향성을 파악할 수 있습니다.',
  'Nasdaq 선물': 'Nasdaq 100 E-mini 선물. 거의 24시간 거래되어 프리마켓·애프터마켓 기술주 흐름을 확인할 수 있습니다.',
};

const MACRO_TOOLTIPS: Record<string, string> = {
  'VIX': '향후 30일 S&P 500의 예상 변동성을 나타내는 "공포 지수". 20 이상이면 불안, 30 이상이면 극도의 공포를 의미합니다.',
  'USD/KRW': '미국 달러 대비 한국 원화 환율. 상승 시 원화 약세(수입 비용 증가), 하락 시 원화 강세를 의미합니다.',
  'DXY': '주요 6개 통화 대비 미국 달러의 강약을 나타내는 달러 인덱스. 상승 시 달러 강세, 하락 시 달러 약세입니다.',
  '10Y Treasury': '미국 10년 만기 국채 금리. 경기 전망과 인플레이션 기대를 반영하며, 주식 시장의 밸류에이션에 큰 영향을 줍니다.',
  'Gold': '대표적인 안전 자산. 경기 불확실성이나 인플레이션 우려 시 상승하는 경향이 있습니다.',
  'Silver': '귀금속이자 산업 금속. 금과 함께 인플레이션 헤지 수단이며 제조업 수요도 반영합니다.',
  'WTI Oil': '서부 텍사스 중질유 가격. 글로벌 경기 활동과 에너지 섹터의 바로미터입니다.',
  'Copper': '"닥터 코퍼"로 불리는 경기 선행 지표. 건설·전자·인프라 수요를 반영해 글로벌 경기 방향을 암시합니다.',
};

const SENTIMENT_KEYS = new Set(['VIX', 'USD/KRW', 'DXY', '10Y Treasury']);
const COMMODITY_KEYS = new Set(['Gold', 'Silver', 'WTI Oil', 'Copper']);

export function MarketOverview() {
  const { data, isLoading, error, refetch } = useMarketOverview();

  if (isLoading) {
    return <PanelLoading title="시장 개요" text="시장 데이터를 불러오고 있어요" />;
  }

  if (error || !data) {
    return (
      <section className="card p-4">
        <p className="text-sm text-danger">시장 데이터를 불러올 수 없습니다.</p>
        <button
          onClick={() => refetch()}
          className="mt-2 cursor-pointer text-xs text-primary hover:underline"
        >
          다시 시도
        </button>
      </section>
    );
  }

  const majorIndices = data.indices.slice(0, 4);
  const futures = data.indices.slice(4);

  return (
    <div className="space-y-6">
      <section aria-label="주요 지수" className="card overflow-hidden">
        <SectionLabel
          tooltip="미국 주요 주가 지수와 변동성 지수입니다. 시장 전반의 분위기를 빠르게 파악할 수 있습니다."
        >
          주요 지수
        </SectionLabel>
        <div className="p-5">
          <IndexCarousel indices={majorIndices} />
          {futures.length > 0 && (
            <div className="mt-5 grid grid-cols-2 gap-3">
              {futures.map((idx) => (
                <FuturesCard key={idx.symbol} index={idx} />
              ))}
            </div>
          )}
        </div>
      </section>

      <MacroSections
        macro={data.macro ?? []}
        usdKrw={data.usdKrw}
        usdKrwChange={data.usdKrwChange}
      />
    </div>
  );
}

function MacroSections({
  macro,
  usdKrw,
  usdKrwChange,
}: {
  macro: MarketIndex[];
  usdKrw?: number | null;
  usdKrwChange?: number | null;
}) {
  const sentiment = macro.filter((m) => SENTIMENT_KEYS.has(m.name));
  const commodities = macro.filter((m) => COMMODITY_KEYS.has(m.name));

  const hasSentiment = sentiment.length > 0 || usdKrw != null;
  const hasCommodities = commodities.length > 0;

  if (!hasSentiment && !hasCommodities) return null;

  return (
    <>
      {hasSentiment && (
        <section aria-label="변동성 · 환율 · 금리" className="card overflow-hidden">
          <SectionLabel tooltip="시장 심리, 환율 변동, 금리 추이를 보여주는 거시경제 지표입니다.">
            변동성 · 환율 · 금리
          </SectionLabel>
          <div className="grid grid-cols-2 gap-3 p-5 sm:grid-cols-4">
            {sentiment.map((item) => (
              <MacroCard key={item.symbol} index={item} />
            ))}
            {usdKrw != null && (
              <UsdKrwCard price={usdKrw} change={usdKrwChange} />
            )}
          </div>
        </section>
      )}
      {hasCommodities && (
        <section aria-label="원자재" className="card overflow-hidden">
          <SectionLabel tooltip="주요 원자재 가격입니다. 인플레이션과 글로벌 경기를 가늠하는 지표입니다.">
            원자재
          </SectionLabel>
          <div className="grid grid-cols-2 gap-3 p-5 sm:grid-cols-4">
            {commodities.map((item) => (
              <MacroCard key={item.symbol} index={item} />
            ))}
          </div>
        </section>
      )}
    </>
  );
}

function SectionLabel({ children, tooltip }: { children: React.ReactNode; tooltip?: string }) {
  return (
    <div className="flex items-center gap-1.5 border-b border-border px-5 py-4">
      <h2 className="text-sm font-semibold text-fg">
        {children}
      </h2>
      {tooltip && <InfoTooltip text={tooltip} />}
    </div>
  );
}

function IndexCarousel({ indices }: { indices: MarketIndex[] }) {
  const [heroIdx, setHeroIdx] = useState(0);
  const [fading, setFading] = useState(false);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const rotate = useCallback(() => {
    setFading(true);
    setTimeout(() => {
      setHeroIdx((prev) => (prev + 1) % indices.length);
      setFading(false);
    }, 300);
  }, [indices.length]);

  useEffect(() => {
    timerRef.current = setInterval(rotate, HERO_ROTATE_MS);
    return () => { if (timerRef.current) clearInterval(timerRef.current); };
  }, [rotate]);

  const handleDotClick = (i: number) => {
    if (i === heroIdx) return;
    if (timerRef.current) clearInterval(timerRef.current);
    setFading(true);
    setTimeout(() => {
      setHeroIdx(i);
      setFading(false);
      timerRef.current = setInterval(rotate, HERO_ROTATE_MS);
    }, 300);
  };

  const hero = indices[heroIdx];
  const rest = indices.filter((_, i) => i !== heroIdx);

  return (
    <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 sm:grid-rows-[1fr_1fr]">
      {hero && (
        <div className="col-span-2 row-span-2 flex flex-col">
          <div className={cn(
            'flex-1 transition-all duration-300',
            fading ? 'scale-[0.98] opacity-0' : 'scale-100 opacity-100',
          )}>
            <HeroIndexCard index={hero} />
          </div>
          <div className="mt-2 flex justify-center gap-1.5">
            {indices.map((idx, i) => (
              <button
                key={idx.symbol}
                onClick={() => handleDotClick(i)}
                aria-label={idx.name}
                className={cn(
                  'h-1.5 rounded-full transition-all duration-300 cursor-pointer',
                  i === heroIdx
                    ? 'w-4 bg-primary'
                    : 'w-1.5 bg-fg-muted/30 hover:bg-fg-muted/50',
                )}
              />
            ))}
          </div>
        </div>
      )}
      {rest.map((idx) => (
        <IndexCard key={idx.symbol} index={idx} />
      ))}
    </div>
  );
}

function changeStyles(change: number) {
  const up = change > 0;
  const down = change < 0;
  return {
    badge: up
      ? 'bg-emerald-500/10 text-success'
      : down
        ? 'bg-red-500/10 text-danger'
        : 'bg-bg-muted text-fg-muted',
    text: up ? 'text-success' : down ? 'text-danger' : 'text-fg-muted',
  };
}

function HeroIndexCard({ index }: { index: MarketIndex }) {
  const { badge, text } = changeStyles(index.change);
  const tooltip = INDEX_TOOLTIPS[index.name];

  return (
    <div className="flex h-full flex-col justify-between rounded-xl bg-bg-muted p-5 ring-1 ring-border">
      <div className="flex items-center justify-between">
        <span className="flex items-center gap-1.5 text-sm font-semibold text-fg">
          {index.name}
          {tooltip && <InfoTooltip text={tooltip} />}
        </span>
        <span className={cn('rounded-full px-2.5 py-1 text-xs font-bold tabular-nums', badge)}>
          {formatPercentChange(index.changePercent)}
        </span>
      </div>
      <div className="mt-4 sm:mt-auto">
        <div className="text-4xl font-extrabold tabular-nums tracking-tight text-fg sm:text-5xl">
          {index.price.toLocaleString('en-US', {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2,
          })}
        </div>
        <div className={cn('mt-2 text-base font-semibold tabular-nums', text)}>
          {formatSignedNumber(index.change)}
        </div>
      </div>
    </div>
  );
}

function IndexCard({ index }: { index: MarketIndex }) {
  const { badge, text } = changeStyles(index.change);
  const tooltip = INDEX_TOOLTIPS[index.name];

  return (
    <div className="flex flex-col justify-between rounded-xl bg-bg-muted p-4 transition-shadow hover:shadow-lg">
      <div className="flex items-center justify-between">
        <span className="flex items-center gap-1 text-xs font-medium text-fg-muted">
          {index.name}
          {tooltip && <InfoTooltip text={tooltip} />}
        </span>
        <span className={cn('rounded-full px-2 py-0.5 text-[11px] font-semibold tabular-nums', badge)}>
          {formatPercentChange(index.changePercent)}
        </span>
      </div>
      <div className="mt-3">
        <div className="text-xl font-bold tabular-nums text-fg">
          {index.price.toLocaleString('en-US', {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2,
          })}
        </div>
        <div className={cn('mt-1 text-sm font-medium tabular-nums', text)}>
          {formatSignedNumber(index.change)}
        </div>
      </div>
    </div>
  );
}

function FuturesCard({ index }: { index: MarketIndex }) {
  const { badge, text } = changeStyles(index.change);
  const tooltip = INDEX_TOOLTIPS[index.name];

  return (
    <div className="flex items-center justify-between rounded-xl bg-bg-muted/60 px-4 py-3 ring-1 ring-border/50">
      <div className="flex items-center gap-3">
        <span className="flex items-center gap-1 text-xs font-medium text-fg-muted">
          {index.name}
          {tooltip && <InfoTooltip text={tooltip} />}
        </span>
        <span className="text-base font-bold tabular-nums text-fg">
          {index.price.toLocaleString('en-US', {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2,
          })}
        </span>
      </div>
      <div className="flex items-center gap-2">
        <span className={cn('text-xs font-medium tabular-nums', text)}>
          {formatSignedNumber(index.change)}
        </span>
        <span className={cn('rounded-full px-2 py-0.5 text-[10px] font-semibold tabular-nums', badge)}>
          {formatPercentChange(index.changePercent)}
        </span>
      </div>
    </div>
  );
}

function MacroCard({ index }: { index: MarketIndex }) {
  const up = index.change > 0;
  const down = index.change < 0;

  const changeBg = up
    ? 'bg-emerald-500/10 text-success'
    : down
      ? 'bg-red-500/10 text-danger'
      : 'bg-bg-muted text-fg-muted';

  const changeColor = up ? 'text-success' : down ? 'text-danger' : 'text-fg-muted';

  const isVix = index.name === 'VIX';
  const isDxy = index.name === 'DXY';
  const isTreasury = index.symbol === '^TNX';
  const noPrefix = isVix || isDxy;
  const priceDisplay = noPrefix
    ? index.price.toFixed(2)
    : isTreasury
      ? `${index.price.toFixed(2)}%`
      : `$${index.price.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

  const vixHighlight =
    isVix && index.price >= 30
      ? 'ring-1 ring-red-500/40 bg-red-500/5'
      : isVix && index.price >= 20
        ? 'ring-1 ring-amber-500/40 bg-amber-500/5'
        : '';

  const tooltip = MACRO_TOOLTIPS[index.name];

  return (
    <div className={cn('rounded-xl bg-bg-muted p-3', vixHighlight)}>
      <div className="flex items-center justify-between">
        <span className="flex items-center gap-1 text-xs font-medium text-fg-muted">
          {index.name}
          {tooltip && <InfoTooltip text={tooltip} />}
        </span>
        <span className={cn('rounded-full px-2 py-0.5 text-[10px] font-semibold tabular-nums', changeBg)}>
          {formatPercentChange(index.changePercent)}
        </span>
      </div>
      <div className="mt-2 text-lg font-bold tabular-nums text-fg">
        {priceDisplay}
      </div>
      <div className={cn('mt-0.5 text-xs font-medium tabular-nums', changeColor)}>
        {formatSignedNumber(index.change)}
      </div>
    </div>
  );
}

function UsdKrwCard({ price, change }: { price: number; change?: number | null }) {
  const up = change != null && change > 0;
  const down = change != null && change < 0;
  const changeBg = up
    ? 'bg-emerald-500/10 text-success'
    : down
      ? 'bg-red-500/10 text-danger'
      : 'bg-bg-muted text-fg-muted';

  const tooltip = MACRO_TOOLTIPS['USD/KRW'];

  return (
    <div className="rounded-xl bg-bg-muted p-3">
      <div className="flex items-center justify-between">
        <span className="flex items-center gap-1 text-xs font-medium text-fg-muted">
          USD/KRW
          {tooltip && <InfoTooltip text={tooltip} />}
        </span>
        {change != null && change !== 0 && (
          <span className={cn('rounded-full px-2 py-0.5 text-[10px] font-semibold tabular-nums', changeBg)}>
            {formatSignedNumber(change)}
          </span>
        )}
      </div>
      <div className="mt-2 text-lg font-bold tabular-nums text-fg">
        {price.toLocaleString('ko-KR', {
          minimumFractionDigits: 2,
          maximumFractionDigits: 2,
        })}
      </div>
      <div className="mt-0.5 text-xs text-fg-muted">원</div>
    </div>
  );
}
