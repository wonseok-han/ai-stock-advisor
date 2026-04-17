'use client';

import { useBookmarks } from '@/features/bookmark/hooks/use-bookmarks';
import { BookmarkCard } from '@/features/my-page/bookmark-card';

export function BookmarkGrid() {
  const { data, isLoading } = useBookmarks();

  if (isLoading) {
    return (
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
        {Array.from({ length: 3 }).map((_, i) => (
          <div
            key={i}
            className="h-24 animate-pulse rounded-lg border border-zinc-200 bg-zinc-50 dark:border-zinc-800 dark:bg-zinc-900"
          />
        ))}
      </div>
    );
  }

  if (!data || data.total === 0) {
    return (
      <div className="rounded-lg border border-dashed border-zinc-300 p-8 text-center dark:border-zinc-700">
        <svg
          className="mx-auto h-10 w-10 text-zinc-300 dark:text-zinc-600"
          fill="none"
          viewBox="0 0 24 24"
          strokeWidth={1}
          stroke="currentColor"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            d="M11.48 3.499a.562.562 0 011.04 0l2.125 5.111a.563.563 0 00.475.345l5.518.442c.499.04.701.663.321.988l-4.204 3.602a.563.563 0 00-.182.557l1.285 5.385a.562.562 0 01-.84.61l-4.725-2.885a.562.562 0 00-.586 0L6.982 20.54a.562.562 0 01-.84-.61l1.285-5.386a.562.562 0 00-.182-.557l-4.204-3.602a.562.562 0 01.321-.988l5.518-.442a.563.563 0 00.475-.345L11.48 3.5z"
          />
        </svg>
        <p className="mt-3 text-sm text-zinc-500 dark:text-zinc-400">
          북마크한 종목이 없습니다.
        </p>
        <p className="mt-1 text-xs text-zinc-400 dark:text-zinc-500">
          종목 상세 페이지에서 별 버튼을 눌러 추가해 보세요.
        </p>
      </div>
    );
  }

  return (
    <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
      {data.bookmarks.map((b) => (
        <BookmarkCard key={b.ticker} bookmark={b} />
      ))}
    </div>
  );
}
