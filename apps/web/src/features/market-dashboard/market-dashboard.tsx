'use client';

import { MarketMovers } from '@/features/market-dashboard/market-movers';
import { MarketNews } from '@/features/market-dashboard/market-news';
import { MarketOverview } from '@/features/market-dashboard/market-overview';

/**
 * 시장 대시보드 래퍼. 3개 독립 섹션 조합.
 * 각 섹션은 독립 React Query hook 으로 로딩/에러 격리.
 * design §7.1
 */
export function MarketDashboard() {
  return (
    <div className="flex flex-col gap-6">
      <MarketOverview />
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <MarketMovers />
        <MarketNews />
      </div>
    </div>
  );
}
