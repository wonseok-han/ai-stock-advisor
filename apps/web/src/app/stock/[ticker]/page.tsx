import { notFound } from 'next/navigation';

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

  return (
    <main className="mx-auto flex w-full max-w-7xl flex-1 flex-col gap-6 px-4 py-6 sm:px-6">
      <StockDetailView ticker={ticker} />
    </main>
  );
}
