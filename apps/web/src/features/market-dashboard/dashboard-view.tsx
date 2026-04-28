'use client';

import { MarketDashboard } from '@/features/market-dashboard/market-dashboard';
import { useMarketOverview } from '@/features/market-dashboard/hooks/use-market-overview';
import { formatKst } from '@/lib/format/date';

export function DashboardView() {
  const { data } = useMarketOverview();

  return (
    <>
      <div className="flex items-end justify-between">
        <div>
          <h1 className="text-xl font-bold text-fg">시장 현황</h1>
          <p className="mt-1 text-sm text-fg-muted">
            미국 주식 시장 요약 · 참고용 정보
          </p>
        </div>
        {(data?.priceLabel || data?.updatedAt) && (
          <span className="text-[11px] tabular-nums text-fg-muted">
            {data.priceLabel ?? `${formatKst(data.updatedAt)} 기준`}
          </span>
        )}
      </div>
      <MarketDashboard />
    </>
  );
}
