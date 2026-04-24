'use client';

import { BookmarkButton } from '@/features/bookmark/bookmark-button';
import { NotificationButton } from '@/features/stock-detail/notification-button';
import { useProfile } from '@/features/stock-detail/hooks/use-profile';
import { useQuote } from '@/features/stock-detail/hooks/use-quote';
import { cn } from '@/lib/cn';
import { formatKst } from '@/lib/format/date';
import { formatUsd } from '@/lib/format/currency';
import { formatPercentChange, formatSignedNumber } from '@/lib/format/percent';

export function StockHeader({ ticker }: { ticker: string }) {
  const { data: profile } = useProfile(ticker);
  const { data: quote, isFetching: quoteFetching } = useQuote(ticker);

  const change = quote?.change ?? 0;
  const up = change > 0;
  const down = change < 0;
  const changeBg = up
    ? 'bg-emerald-500/10 text-success'
    : down
      ? 'bg-red-500/10 text-danger'
      : 'bg-bg-muted text-fg-muted';

  return (
    <header className="flex flex-wrap items-end justify-between gap-4">
      <div>
        <div className="flex items-center gap-3">
          <h1 className="text-2xl font-bold text-fg">{ticker}</h1>
          {profile?.exchange && (
            <span className="rounded-md bg-bg-muted px-2 py-0.5 text-xs font-medium text-fg-secondary">
              {profile.exchange}
            </span>
          )}
          {quote?.marketStatus === 'OPEN' && (
            <span className="flex items-center gap-1 rounded-md bg-emerald-500/10 px-2 py-0.5 text-xs font-semibold text-success">
              <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-success" />
              LIVE
            </span>
          )}
        </div>
        {profile?.name && (
          <p className="mt-1 text-sm text-fg-muted">{profile.name}</p>
        )}
        <div className="mt-1 flex items-center gap-2">
          <BookmarkButton ticker={ticker} />
          <NotificationButton ticker={ticker} />
        </div>
      </div>
      <div className="text-right">
        <div className="text-3xl font-bold tabular-nums text-fg">
          {formatUsd(quote?.price)}
        </div>
        <div className="mt-1 flex items-center justify-end gap-2">
          <span className={cn('rounded-md px-2 py-0.5 text-sm font-semibold tabular-nums', changeBg)}>
            {formatSignedNumber(quote?.change)} ({formatPercentChange(quote?.changePercent)})
          </span>
        </div>
        <div className="mt-1 text-xs text-fg-muted">
          {quote?.priceLabel ?? `업데이트: ${formatKst(quote?.updatedAt)}`}
          {quoteFetching && ' · 새로고침 중'}
        </div>
      </div>
    </header>
  );
}
