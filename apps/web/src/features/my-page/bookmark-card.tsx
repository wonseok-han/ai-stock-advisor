'use client';

import Link from 'next/link';

import { useRemoveBookmark } from '@/features/bookmark/hooks/use-bookmarks';
import { useSnackbarStore } from '@/stores/use-snackbar-store';
import { cn } from '@/lib/cn';

import type { Bookmark } from '@/types/bookmark';

export function BookmarkCard({ bookmark }: { bookmark: Bookmark }) {
  const removeMutation = useRemoveBookmark();
  const showSnackbar = useSnackbarStore((s) => s.show);
  const up = (bookmark.changePercent ?? 0) >= 0;

  function handleRemove() {
    removeMutation.mutate(bookmark.ticker, {
      onSuccess: () => showSnackbar('북마크를 해제했습니다'),
    });
  }

  return (
    <div className="group relative rounded-xl bg-bg-surface p-4 shadow-sm transition-all hover:shadow-md dark:bg-bg-surface/80">
      <Link href={`/stock/${bookmark.ticker}`} className="block">
        <div className="flex items-center justify-between">
          <div className="min-w-0">
            <span className="text-base font-bold text-fg">
              {bookmark.ticker}
            </span>
            {bookmark.name && (
              <p className="mt-0.5 truncate text-xs text-fg-muted">
                {bookmark.name}
              </p>
            )}
          </div>
          <div className="text-right">
            {bookmark.price != null && (
              <span className="text-sm font-medium tabular-nums text-fg">
                ${bookmark.price.toFixed(2)}
              </span>
            )}
            {bookmark.changePercent != null && (
              <p
                className={cn(
                  'text-xs font-semibold tabular-nums',
                  up ? 'text-success' : 'text-danger',
                )}
              >
                {up ? '+' : ''}{bookmark.changePercent.toFixed(2)}%
              </p>
            )}
          </div>
        </div>
      </Link>
      <button
        onClick={handleRemove}
        disabled={removeMutation.isPending}
        className="absolute -right-2 -top-2 flex h-6 w-6 items-center justify-center rounded-full border border-border bg-bg text-fg-muted opacity-0 shadow-sm transition-all hover:text-danger group-hover:opacity-100 disabled:opacity-50 max-sm:opacity-100"
        aria-label={`${bookmark.ticker} 북마크 해제`}
      >
        <svg className="h-3 w-3" fill="none" viewBox="0 0 24 24" strokeWidth={2.5} stroke="currentColor">
          <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
        </svg>
      </button>
    </div>
  );
}
