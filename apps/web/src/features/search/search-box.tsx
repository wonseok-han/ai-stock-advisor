'use client';

import Link from 'next/link';
import { useState } from 'react';

import { useSearch } from '@/features/search/hooks/use-search';
import { cn } from '@/lib/cn';

/**
 * 종목 검색 + 자동완성 (design §4.1).
 * BE /api/v1/stocks/search 호출 (debounce 300ms).
 * Enter 시 첫 결과로 이동. 결과 클릭은 Link.
 */
export function SearchBox({ className }: { className?: string }) {
  const [query, setQuery] = useState('');
  const { data, isFetching, error } = useSearch(query);
  const hits = data ?? [];
  const trimmed = query.trim();

  return (
    <div className={cn('w-full max-w-xl', className)}>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          if (hits.length > 0) {
            window.location.href = `/stock/${hits[0].ticker}`;
          }
        }}
      >
        <input
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="종목 검색 (예: AAPL, Tesla)"
          maxLength={20}
          aria-label="종목 검색"
          className="w-full rounded-lg border border-border bg-bg-surface px-4 py-3 text-base text-fg shadow-sm outline-none focus:border-zinc-500 focus:ring-2 focus:ring-zinc-200 dark:focus:border-zinc-400 dark:focus:ring-zinc-700"
        />
      </form>

      {trimmed.length > 0 && (
        <div
          className="mt-2 overflow-hidden rounded-lg border border-border bg-bg-surface shadow-sm"
          role="listbox"
          aria-label="검색 결과"
        >
          {isFetching && (
            <div className="px-4 py-2 text-sm text-fg-muted">검색 중…</div>
          )}
          {!isFetching && error && (
            <div className="px-4 py-2 text-sm text-danger">
              검색을 일시적으로 사용할 수 없습니다. 잠시 후 다시 시도해 주세요.
            </div>
          )}
          {!isFetching && !error && hits.length === 0 && (
            <div className="px-4 py-2 text-sm text-fg-muted">
              결과가 없습니다.
            </div>
          )}
          {hits.map((hit) => (
            <Link
              key={hit.ticker}
              href={`/stock/${hit.ticker}`}
              className="flex items-baseline justify-between gap-4 px-4 py-2 hover:bg-bg-muted"
              role="option"
              aria-selected="false"
            >
              <span className="font-semibold text-fg">
                {hit.ticker}
              </span>
              <span className="truncate text-sm text-fg-secondary">
                {hit.name}
              </span>
              <span className="shrink-0 text-xs text-fg-muted">
                {hit.exchange}
              </span>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
