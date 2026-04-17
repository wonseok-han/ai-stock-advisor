'use client';

import Link from 'next/link';

import { useBookmarks, useRemoveBookmark } from '@/features/bookmark/hooks/use-bookmarks';

export function BookmarkList() {
  const { data, isLoading } = useBookmarks();
  const removeMutation = useRemoveBookmark();

  if (isLoading) {
    return <p className="text-sm text-gray-500">로딩 중...</p>;
  }

  if (!data || data.total === 0) {
    return (
      <div className="rounded-lg border border-dashed border-gray-300 p-8 text-center dark:border-gray-700">
        <p className="text-sm text-gray-500 dark:text-gray-400">북마크한 종목이 없습니다.</p>
        <p className="mt-1 text-xs text-gray-400 dark:text-gray-500">
          종목 상세 페이지에서 ☆ 버튼을 눌러 추가해 보세요.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-2">
      {data.bookmarks.map((b) => (
        <div
          key={b.ticker}
          className="flex items-center justify-between rounded-lg border border-gray-200 px-4 py-3 dark:border-gray-700"
        >
          <Link href={`/stock/${b.ticker}`} className="flex-1">
            <div className="flex items-center gap-3">
              <span className="font-semibold text-gray-900 dark:text-white">{b.ticker}</span>
              <span className="text-sm text-gray-500 dark:text-gray-400">{b.name}</span>
            </div>
            {b.price != null && (
              <div className="mt-0.5 flex gap-2 text-sm">
                <span className="text-gray-700 dark:text-gray-300">${b.price.toFixed(2)}</span>
                {b.changePercent != null && (
                  <span className={b.changePercent >= 0 ? 'text-green-600' : 'text-red-600'}>
                    {b.changePercent >= 0 ? '+' : ''}{b.changePercent.toFixed(2)}%
                  </span>
                )}
              </div>
            )}
          </Link>
          <button
            onClick={() => removeMutation.mutate(b.ticker)}
            disabled={removeMutation.isPending}
            className="ml-3 cursor-pointer text-sm text-gray-400 hover:text-red-500 disabled:opacity-50"
            aria-label={`${b.ticker} 북마크 해제`}
          >
            삭제
          </button>
        </div>
      ))}
    </div>
  );
}
