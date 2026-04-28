import { dehydrate, HydrationBoundary, QueryClient } from '@tanstack/react-query';
import { notFound } from 'next/navigation';

import { getProfile, getQuote } from '@/lib/api/stocks';
import { AiSignalPageView } from '@/features/stock-detail/ai-signal/ai-signal-page-view';

import type { Metadata } from 'next';

const TICKER_REGEX = /^[A-Z]{1,5}(\.[A-Z])?$/;

interface Props {
  params: Promise<{ ticker: string }>;
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { ticker } = await params;
  return {
    title: `${ticker} AI 분석 · 지금이니?!`,
    description: `${ticker} 종목의 AI 참고 분석 시그널. 투자 자문이 아닙니다.`,
  };
}

export default async function AiSignalPage({ params }: Props) {
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
  ]);

  return (
    <main className="mx-auto flex w-full max-w-7xl flex-1 flex-col gap-6 px-4 py-6 sm:px-6">
      <HydrationBoundary state={dehydrate(queryClient)}>
        <AiSignalPageView ticker={ticker} />
      </HydrationBoundary>
    </main>
  );
}
