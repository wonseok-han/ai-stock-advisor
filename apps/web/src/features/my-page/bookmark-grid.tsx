'use client';

import { useBookmarks } from '@/features/bookmark/hooks/use-bookmarks';
import { BookmarkCard } from '@/features/my-page/bookmark-card';

export function BookmarkGrid() {
  const { data, isLoading } = useBookmarks();

  if (isLoading) {
    return (
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
        {Array.from({ length: 3 }).map((_, i) => (
          <div
            key={i}
            className="h-24 animate-pulse rounded-xl bg-bg-skeleton"
          />
        ))}
      </div>
    );
  }

  if (!data || data.total === 0) {
    return (
      <div className="rounded-xl bg-bg-muted/50 p-10 text-center">
        <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-amber-100 dark:bg-amber-900/20">
          <svg
            className="h-7 w-7 text-amber-500"
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
        </div>
        <p className="mt-4 text-sm font-medium text-fg-secondary">
          아직 북마크한 종목이 없습니다
        </p>
        <p className="mt-1.5 text-xs text-fg-muted">
          종목 상세에서 ☆ 버튼을 눌러 관심 종목을 추가해 보세요
        </p>
      </div>
    );
  }

  return (
    <div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
      {data.bookmarks.map((b) => (
        <BookmarkCard key={b.ticker} bookmark={b} />
      ))}
    </div>
  );
}
