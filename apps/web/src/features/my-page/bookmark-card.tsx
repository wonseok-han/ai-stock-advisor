'use client';

import Link from 'next/link';

import { useRemoveBookmark } from '@/features/bookmark/hooks/use-bookmarks';
import { cn } from '@/lib/cn';

import type { Bookmark } from '@/types/bookmark';

export function BookmarkCard({ bookmark }: { bookmark: Bookmark }) {
  const removeMutation = useRemoveBookmark();
  const up = (bookmark.changePercent ?? 0) >= 0;

  return (
    <div className="rounded-lg border border-border bg-bg-surface p-4 transition-shadow hover:shadow-sm">
      <div className="flex items-start justify-between">
        <Link href={`/stock/${bookmark.ticker}`} className="min-w-0 flex-1">
          <span className="text-base font-semibold text-fg">
            {bookmark.ticker}
          </span>
          {bookmark.name && (
            <p className="mt-0.5 truncate text-xs text-fg-muted">
              {bookmark.name}
            </p>
          )}
          <div className="mt-1.5 flex items-baseline gap-2">
            {bookmark.price != null && (
              <span className="text-sm tabular-nums text-fg-secondary">
                ${bookmark.price.toFixed(2)}
              </span>
            )}
            {bookmark.changePercent != null && (
              <span
                className={cn(
                  'text-sm font-medium tabular-nums',
                  up ? 'text-success' : 'text-danger',
                )}
              >
                {up ? '+' : ''}{bookmark.changePercent.toFixed(2)}%
              </span>
            )}
          </div>
        </Link>
        <button
          onClick={() => removeMutation.mutate(bookmark.ticker)}
          disabled={removeMutation.isPending}
          className="ml-2 shrink-0 cursor-pointer rounded p-1 text-fg-muted transition-colors hover:bg-red-50 hover:text-danger disabled:opacity-50 dark:hover:bg-red-900/20"
          aria-label={`${bookmark.ticker} 북마크 해제`}
        >
          <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      </div>
    </div>
  );
}
