'use client';

import { InfoTooltip } from '@/components/ui/info-tooltip';
import { EarningsHistory } from '@/features/stock-detail/analyst/components/earnings-history';
import { PriceTargetBar } from '@/features/stock-detail/analyst/components/price-target-bar';
import { RatingGauge } from '@/features/stock-detail/analyst/components/rating-gauge';
import { useAnalystEstimates } from '@/features/stock-detail/analyst/hooks/use-analyst-estimates';

export function AnalystPanel({ ticker }: { ticker: string }) {
  const { data, isLoading, error } = useAnalystEstimates(ticker);

  if (isLoading) {
    return (
      <section className="card p-5 text-sm text-fg-muted">
        애널리스트 데이터 불러오는 중…
      </section>
    );
  }

  if (error || !data) return null;

  const hasRating = data.rating != null;
  const hasPriceTarget = data.priceTarget != null;
  const hasEarnings = data.earnings.length > 0;

  if (!hasRating && !hasPriceTarget && !hasEarnings) return null;

  return (
    <section aria-label="애널리스트 컨센서스" className="card p-5">
      <div className="flex items-center justify-between">
        <h2 className="text-sm font-semibold text-fg">애널리스트 컨센서스</h2>
        <InfoTooltip text="월가 애널리스트들의 종합 의견입니다. 투자 판단의 참고 자료로만 활용하세요." />
      </div>

      {(hasRating || hasPriceTarget) && (
        <div className="mt-4 grid grid-cols-1 gap-4 sm:grid-cols-2">
          {hasRating && (
            <RatingGauge
              score={data.rating!.score}
              labelKo={data.rating!.labelKo}
              totalAnalysts={data.rating!.totalAnalysts}
              distribution={data.rating!.distribution}
            />
          )}
          {hasPriceTarget && (
            <PriceTargetBar
              current={data.priceTarget!.current}
              high={data.priceTarget!.high}
              low={data.priceTarget!.low}
              mean={data.priceTarget!.mean}
              upsidePercent={data.priceTarget!.upsidePercent}
            />
          )}
        </div>
      )}

      {hasEarnings && (
        <div className="mt-4">
          <EarningsHistory earnings={data.earnings} />
        </div>
      )}
    </section>
  );
}
