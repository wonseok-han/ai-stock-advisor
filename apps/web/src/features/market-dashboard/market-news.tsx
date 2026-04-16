'use client';

import { useMarketNews } from '@/features/market-dashboard/hooks/use-market-news';

import type { MarketNewsItem } from '@/types/market';

/**
 * 시장 뉴스 피드.
 * 한국어 제목 우선, 없으면 영문 제목 표시.
 * 제목 클릭 → 원문 링크 새 탭.
 * design §7.4
 */
export function MarketNews() {
  const { data, isLoading, error, refetch } = useMarketNews();

  if (isLoading) {
    return (
      <section
        aria-label="시장 뉴스"
        className="rounded-lg border border-zinc-200 bg-white p-4 dark:border-zinc-800 dark:bg-zinc-900"
      >
        <div className="h-4 w-24 animate-pulse rounded bg-zinc-100 dark:bg-zinc-800" />
        <div className="mt-3 space-y-3">
          {Array.from({ length: 5 }).map((_, i) => (
            <div key={i} className="space-y-1">
              <div className="h-4 animate-pulse rounded bg-zinc-100 dark:bg-zinc-800" />
              <div className="h-3 w-3/4 animate-pulse rounded bg-zinc-100 dark:bg-zinc-800" />
            </div>
          ))}
        </div>
      </section>
    );
  }

  if (error || !data) {
    return (
      <section className="rounded-lg border border-zinc-200 bg-white p-4 dark:border-zinc-800 dark:bg-zinc-900">
        <p className="text-sm text-red-600">
          시장 뉴스를 불러올 수 없습니다.
        </p>
        <button
          onClick={() => refetch()}
          className="mt-2 text-xs text-blue-600 hover:underline dark:text-blue-400"
        >
          다시 시도
        </button>
      </section>
    );
  }

  if (data.length === 0) {
    return (
      <section className="rounded-lg border border-zinc-200 bg-white p-4 text-sm text-zinc-500 dark:border-zinc-800 dark:bg-zinc-900">
        최근 시장 뉴스가 없습니다.
      </section>
    );
  }

  return (
    <section
      aria-label="시장 뉴스"
      className="rounded-lg border border-zinc-200 bg-white p-4 dark:border-zinc-800 dark:bg-zinc-900"
    >
      <h2 className="mb-3 text-sm font-semibold text-zinc-700 dark:text-zinc-300">
        시장 뉴스 (참고용)
      </h2>
      <ul className="flex flex-col divide-y divide-zinc-100 dark:divide-zinc-800">
        {data.map((item) => (
          <NewsRow key={item.id} item={item} />
        ))}
      </ul>
      <p className="mt-3 text-xs text-zinc-500">{data[0]?.disclaimer}</p>
    </section>
  );
}

function NewsRow({ item }: { item: MarketNewsItem }) {
  const title = item.titleKo ?? item.titleEn;

  return (
    <li className="flex flex-col gap-1 py-3">
      <a
        href={item.sourceUrl}
        target="_blank"
        rel="noreferrer noopener"
        className="text-sm font-medium text-zinc-900 hover:underline dark:text-zinc-100"
      >
        {title}
      </a>
      {item.summaryKo && (
        <p className="line-clamp-2 text-xs text-zinc-600 dark:text-zinc-400">
          {item.summaryKo}
        </p>
      )}
      <div className="flex items-center gap-2 text-[11px] text-zinc-500">
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
