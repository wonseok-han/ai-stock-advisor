'use client';

import { MarketMovers } from '@/features/market-dashboard/market-movers';
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
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <MarketMovers />
        <MarketNews />
      </div>
    </div>
  );
}
