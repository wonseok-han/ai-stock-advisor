'use client';

import { MarketNews } from '@/features/market-dashboard/market-news';
import { MarketOverview } from '@/features/market-dashboard/market-overview';
import { MarketRegime } from '@/features/market-dashboard/market-regime';
import { SectorPerformance } from '@/features/market-dashboard/sector-performance';

export function MarketDashboard() {
  return (
    <div className="flex flex-col gap-6">
      <MarketOverview />
      <SectorPerformance />
      <MarketRegime />
      <MarketNews />
    </div>
  );
}
