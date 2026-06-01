'use client';

import { PanelLoading } from '@/components/ui/panel-loading';
import { useMarketNews } from '@/features/market-dashboard/hooks/use-market-news';

import type { MarketNewsItem } from '@/types/market';

export function MarketNews() {
  const {
    data,
    isLoading,
    error,
    refetch,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
  } = useMarketNews();

  if (isLoading) {
    return <PanelLoading title="시장 뉴스" text="최신 뉴스를 불러오고 있어요" />;
  }

  const items = data?.pages.flat() ?? [];

  if (error && items.length === 0) {
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

  if (items.length === 0) {
    return (
      <section className="card p-5 text-sm text-fg-muted">
        최근 시장 뉴스가 없습니다.
      </section>
    );
  }

  return (
    <section aria-label="시장 뉴스" className="card overflow-hidden">
      <div className="border-b border-border px-5 py-4">
        <h2 className="text-sm font-semibold text-fg">시장 뉴스</h2>
      </div>
      <ul className="divide-y divide-border">
        {items.map((item) => (
          <NewsRow key={item.id} item={item} />
        ))}
      </ul>
      {hasNextPage && (
        <div className="border-t border-border px-5 py-3">
          <button
            onClick={() => fetchNextPage()}
            disabled={isFetchingNextPage}
            className="w-full cursor-pointer rounded-lg border border-border py-2 text-xs font-medium text-fg-secondary transition-colors hover:bg-bg-muted/50 disabled:cursor-default disabled:opacity-60"
          >
            {isFetchingNextPage ? '불러오는 중…' : '더 보기'}
          </button>
        </div>
      )}
      <div className="border-t border-border px-5 py-2.5">
        <p className="text-[11px] text-fg-muted">{items[0]?.disclaimer}</p>
      </div>
    </section>
  );
}

function NewsRow({ item }: { item: MarketNewsItem }) {
  const title = item.titleKo ?? item.titleEn;

  return (
    <li className="px-5 py-3.5 transition-colors hover:bg-bg-muted/50">
      <a
        href={item.sourceUrl}
        target="_blank"
        rel="noreferrer noopener"
        className="text-sm font-medium text-fg transition-colors hover:text-primary"
      >
        {title}
      </a>
      {item.summaryKo && (
        <p className="mt-1 line-clamp-2 text-xs text-fg-secondary">{item.summaryKo}</p>
      )}
      <div className="mt-1.5 flex items-center gap-2 text-[11px] text-fg-muted">
        <span className="rounded bg-bg-muted px-1.5 py-0.5 text-[10px] font-medium">{item.source}</span>
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
