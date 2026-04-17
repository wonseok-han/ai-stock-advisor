'use client';

import { BookmarkButton } from '@/features/bookmark/bookmark-button';
import { NotificationButton } from '@/features/stock-detail/notification-button';
import { useProfile } from '@/features/stock-detail/hooks/use-profile';
import { useQuote } from '@/features/stock-detail/hooks/use-quote';
import { cn } from '@/lib/cn';
import { formatKst } from '@/lib/format/date';
import { formatUsd } from '@/lib/format/currency';
import { formatPercentChange, formatSignedNumber } from '@/lib/format/percent';

/**
 * 종목 헤더 (design §4.2). 티커/이름/거래소/현재가/등락.
 * Profile 은 24h 캐시, Quote 는 30s refetch.
 */
export function StockHeader({ ticker }: { ticker: string }) {
  const { data: profile } = useProfile(ticker);
  const { data: quote, isFetching: quoteFetching } = useQuote(ticker);

  const change = quote?.change ?? 0;
  const up = change > 0;
  const down = change < 0;
  const colorClass = up
    ? 'text-green-600 dark:text-green-500'
    : down
      ? 'text-red-600 dark:text-red-500'
      : 'text-zinc-500';

  return (
    <header className="flex flex-wrap items-end justify-between gap-4">
      <div>
        <div className="flex items-center gap-3">
          <h1 className="text-2xl font-semibold text-zinc-900 dark:text-zinc-50">
            {ticker}
          </h1>
          {profile?.exchange && (
            <span className="rounded-full bg-zinc-100 px-2 py-0.5 text-xs text-zinc-600 dark:bg-zinc-800 dark:text-zinc-400">
              {profile.exchange}
            </span>
          )}
          <BookmarkButton ticker={ticker} />
          <NotificationButton ticker={ticker} />
        </div>
        {profile?.name && (
          <p className="mt-1 text-sm text-zinc-500">{profile.name}</p>
        )}
      </div>
      <div className="text-right">
        <div className="text-2xl font-semibold tabular-nums text-zinc-900 dark:text-zinc-50">
          {formatUsd(quote?.price)}
        </div>
        <div className={cn('text-sm tabular-nums', colorClass)}>
          {formatSignedNumber(quote?.change)}{' '}
          ({formatPercentChange(quote?.changePercent)})
        </div>
        <div className="mt-1 text-xs text-zinc-400">
          업데이트: {formatKst(quote?.updatedAt)}
          {quoteFetching && ' · 새로고침 중'}
        </div>
      </div>
    </header>
  );
}
