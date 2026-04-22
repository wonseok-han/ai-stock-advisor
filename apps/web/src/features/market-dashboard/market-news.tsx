'use client';

import { useMarketNews } from '@/features/market-dashboard/hooks/use-market-news';

import type { MarketNewsItem } from '@/types/market';

export function MarketNews() {
  const { data, isLoading, error, refetch } = useMarketNews();

  if (isLoading) {
    return (
      <section aria-label="시장 뉴스" className="card p-5">
        <div className="h-4 w-24 animate-pulse rounded bg-bg-muted" />
        <div className="mt-4 space-y-3">
          {Array.from({ length: 5 }).map((_, i) => (
            <div key={i} className="space-y-1">
              <div className="h-4 animate-pulse rounded bg-bg-muted" />
              <div className="h-3 w-3/4 animate-pulse rounded bg-bg-muted" />
            </div>
          ))}
        </div>
      </section>
    );
  }

  if (error || !data) {
    return (
      <section className="card p-5">
        <p className="text-sm text-danger">시장 뉴스를 불러올 수 없습니다.</p>
        <button
          onClick={() => refetch()}
          className="mt-2 cursor-pointer text-xs text-primary hover:underline"
        >
          다시 시도
        </button>
      </section>
    );
  }

  if (data.length === 0) {
    return (
      <section className="card p-5 text-sm text-fg-muted">
        최근 시장 뉴스가 없습니다.
      </section>
    );
  }

  return (
    <section aria-label="시장 뉴스" className="card p-5">
      <h2 className="text-sm font-semibold text-fg">시장 뉴스</h2>
      <ul className="mt-4 flex flex-col divide-y divide-border">
        {data.map((item) => (
          <NewsRow key={item.id} item={item} />
        ))}
      </ul>
      <p className="mt-4 text-[11px] text-fg-muted">{data[0]?.disclaimer}</p>
    </section>
  );
}

function NewsRow({ item }: { item: MarketNewsItem }) {
  const title = item.titleKo ?? item.titleEn;

  return (
    <li className="flex flex-col gap-1 py-3 first:pt-0">
      <a
        href={item.sourceUrl}
        target="_blank"
        rel="noreferrer noopener"
        className="text-sm font-medium text-fg transition-colors hover:text-primary"
      >
        {title}
      </a>
      {item.summaryKo && (
        <p className="line-clamp-2 text-xs text-fg-secondary">{item.summaryKo}</p>
      )}
      <div className="flex items-center gap-2 text-[11px] text-fg-muted">
        <span>{item.source}</span>
        <span aria-hidden="true">&middot;</span>
        <time>{formatRelativeTime(item.publishedAt)}</time>
      </div>
    </li>
  );
}

function formatRelativeTime(epochSeconds: number): string {
  const now = Date.now();
  const then = epochSeconds * 1000;
  const diffMs = now - then;

  if (diffMs < 0) return '방금 전';

  const minutes = Math.floor(diffMs / 60_000);
  if (minutes < 1) return '방금 전';
  if (minutes < 60) return `${minutes}분 전`;

  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}시간 전`;

  const days = Math.floor(hours / 24);
  if (days < 7) return `${days}일 전`;

  return new Date(then).toLocaleDateString('ko-KR');
}
