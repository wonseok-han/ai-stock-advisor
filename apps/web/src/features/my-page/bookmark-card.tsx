'use client';

import Link from 'next/link';

import { useRemoveBookmark } from '@/features/bookmark/hooks/use-bookmarks';
import { cn } from '@/lib/cn';

import type { Bookmark } from '@/types/bookmark';

export function BookmarkCard({ bookmark }: { bookmark: Bookmark }) {
  const removeMutation = useRemoveBookmark();
  const up = (bookmark.changePercent ?? 0) >= 0;

  return (
    <div className="relative rounded-lg border border-zinc-200 bg-white p-4 transition-shadow hover:shadow-sm dark:border-zinc-800 dark:bg-zinc-900">
      <Link href={`/stock/${bookmark.ticker}`} className="block">
        <div className="flex items-baseline justify-between">
          <span className="text-base font-semibold text-zinc-900 dark:text-zinc-100">
            {bookmark.ticker}
          </span>
          {bookmark.price != null && (
            <span className="text-sm tabular-nums text-zinc-700 dark:text-zinc-300">
              ${bookmark.price.toFixed(2)}
            </span>
          )}
        </div>
        {bookmark.name && (
          <p className="mt-0.5 truncate text-xs text-zinc-500 dark:text-zinc-400">
            {bookmark.name}
          </p>
        )}
        {bookmark.changePercent != null && (
          <p
            className={cn(
              'mt-1 text-sm font-medium tabular-nums',
              up ? 'text-green-600 dark:text-green-500' : 'text-red-600 dark:text-red-500',
            )}
          >
            {up ? '+' : ''}{bookmark.changePercent.toFixed(2)}%
          </p>
        )}
      </Link>
      <button
        onClick={(e) => {
          e.preventDefault();
          removeMutation.mutate(bookmark.ticker);
        }}
        disabled={removeMutation.isPending}
        className="absolute right-2 top-2 rounded p-1 text-zinc-400 transition-colors hover:text-red-500 disabled:opacity-50"
        aria-label={`${bookmark.ticker} 북마크 해제`}
      >
        <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor">
          <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
        </svg>
      </button>
    </div>
  );
}
