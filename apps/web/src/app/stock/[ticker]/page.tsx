import { dehydrate, HydrationBoundary, QueryClient } from '@tanstack/react-query';
import { notFound } from 'next/navigation';

import { getNews } from '@/lib/api/news';
import {
  getAnalystEstimates,
  getCandles,
  getCompanyOverview,
  getIndicators,
  getProfile,
  getQuote,
} from '@/lib/api/stocks';
import { StockDetailView } from '@/features/stock-detail/stock-detail-view';

import type { Metadata } from 'next';

const TICKER_REGEX = /^[A-Z]{1,5}(\.[A-Z])?$/;

interface Props {
  params: Promise<{ ticker: string }>;
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { ticker } = await params;
  return {
    title: `${ticker} · 지금이니?!`,
    description: `${ticker} 종목의 시세/차트/지표 참고 정보. 투자 자문이 아닙니다.`,
  };
}

export default async function StockDetailPage({ params }: Props) {
  const { ticker: raw } = await params;
  const ticker = raw.toUpperCase();
  if (!TICKER_REGEX.test(ticker)) {
    notFound();
  }

  const queryClient = new QueryClient();

  await Promise.all([
    queryClient.prefetchQuery({
      queryKey: ['profile', ticker],
      queryFn: () => getProfile(ticker),
    }),
    queryClient.prefetchQuery({
      queryKey: ['quote', ticker],
      queryFn: () => getQuote(ticker),
    }),
    queryClient.prefetchQuery({
      queryKey: ['candles', ticker, '1D'],
      queryFn: () => getCandles(ticker, '1D'),
    }),
    queryClient.prefetchQuery({
      queryKey: ['indicators', ticker],
      queryFn: () => getIndicators(ticker),
    }),
    queryClient.prefetchQuery({
      queryKey: ['overview', ticker],
      queryFn: () => getCompanyOverview(ticker),
    }),
    queryClient.prefetchQuery({
      queryKey: ['analyst', ticker],
      queryFn: async () => (await getAnalystEstimates(ticker)) ?? null,
    }),
    queryClient.prefetchQuery({
      queryKey: ['news', ticker, 5],
      queryFn: () => getNews(ticker, 5),
    }),
  ]);

  return (
    <main className="mx-auto flex w-full max-w-7xl flex-1 flex-col gap-6 px-4 py-6 sm:px-6">
      <HydrationBoundary state={dehydrate(queryClient)}>
        <StockDetailView ticker={ticker} />
      </HydrationBoundary>
    </main>
  );
}
