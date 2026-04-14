import { notFound } from 'next/navigation';

import { ChartPanel } from '@/features/stock-detail/chart/chart-panel';

import type { Metadata } from 'next';

const TICKER_REGEX = /^[A-Z]{1,5}(\.[A-Z])?$/;

interface Props {
  params: Promise<{ ticker: string }>;
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { ticker } = await params;
  return {
    title: `${ticker} · AI Stock Advisor`,
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
    <main className="mx-auto flex w-full max-w-5xl flex-1 flex-col gap-6 px-4 py-8 sm:px-6">
      <header>
        <h1 className="text-2xl font-semibold text-zinc-900 dark:text-zinc-50">
          {ticker}
        </h1>
        <p className="mt-1 text-sm text-zinc-500">
          참고용 정보이며 투자 자문이 아닙니다. 투자 판단과 책임은 사용자 본인에게
          있습니다.
        </p>
      </header>

      <ChartPanel ticker={ticker} tf="1D" />
    </main>
  );
}
