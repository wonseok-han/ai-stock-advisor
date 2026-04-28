'use client';

import { useState } from 'react';

import { AiSignalLinkCard } from '@/features/stock-detail/ai-signal/ai-signal-link-card';
import { AnalystPanel } from '@/features/stock-detail/analyst/analyst-panel';
import { CompanyOverviewPanel } from '@/features/stock-detail/company-overview-panel';
import { NewsPanel } from '@/features/stock-detail/news/news-panel';
import { ChartPanel } from '@/features/stock-detail/chart/chart-panel';
import { IndicatorsPanel } from '@/features/stock-detail/indicators/indicators-panel';
import { SecFilingsPanel } from '@/features/stock-detail/sec-filings/sec-filings-panel';
import { StockHeader } from '@/features/stock-detail/stock-header';
import { TimeFrameTabs } from '@/features/stock-detail/time-frame-tabs';

import type { TimeFrame } from '@/types/stock';

/**
 * 종목 상세 클라이언트 컨테이너. 선택된 TimeFrame 을 차트와 공유.
 * Server Component(page.tsx) 는 검증/메타데이터만 담당.
 */
export function StockDetailView({ ticker }: { ticker: string }) {
  const [tf, setTf] = useState<TimeFrame>('1D');

  return (
    <div className="flex flex-col gap-6">
      <StockHeader ticker={ticker} />
      <AiSignalLinkCard ticker={ticker} />
      <CompanyOverviewPanel ticker={ticker} />
      <AnalystPanel ticker={ticker} />
      <TimeFrameTabs value={tf} onChange={setTf} />
      <ChartPanel ticker={ticker} tf={tf} />
      <IndicatorsPanel ticker={ticker} />
      <SecFilingsPanel ticker={ticker} />
      <NewsPanel ticker={ticker} />
    </div>
  );
}
