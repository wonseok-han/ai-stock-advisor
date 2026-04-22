'use client';

import Link from 'next/link';

import { useBookmarks, useRemoveBookmark } from '@/features/bookmark/hooks/use-bookmarks';

export function BookmarkList() {
  const { data, isLoading } = useBookmarks();
  const removeMutation = useRemoveBookmark();

  if (isLoading) {
    return <p className="text-sm text-fg-muted">로딩 중...</p>;
  }

  if (!data || data.total === 0) {
    return (
      <div className="rounded-lg border border-dashed border-border p-8 text-center">
        <p className="text-sm text-fg-muted">북마크한 종목이 없습니다.</p>
        <p className="mt-1 text-xs text-fg-muted">
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
          className="flex items-center justify-between rounded-lg border border-border px-4 py-3"
        >
          <Link href={`/stock/${b.ticker}`} className="flex-1">
            <div className="flex items-center gap-3">
              <span className="font-semibold text-fg">{b.ticker}</span>
              <span className="text-sm text-fg-muted">{b.name}</span>
            </div>
            {b.price != null && (
              <div className="mt-0.5 flex gap-2 text-sm">
                <span className="text-fg-secondary">${b.price.toFixed(2)}</span>
                {b.changePercent != null && (
                  <span className={b.changePercent >= 0 ? 'text-success' : 'text-danger'}>
                    {b.changePercent >= 0 ? '+' : ''}{b.changePercent.toFixed(2)}%
                  </span>
                )}
              </div>
            )}
          </Link>
          <button
            onClick={() => removeMutation.mutate(b.ticker)}
            disabled={removeMutation.isPending}
            className="ml-3 cursor-pointer text-sm text-fg-muted hover:text-danger disabled:opacity-50"
            aria-label={`${b.ticker} 북마크 해제`}
          >
            삭제
          </button>
        </div>
      ))}
    </div>
  );
}
