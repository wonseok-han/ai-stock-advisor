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
          className="w-full rounded-lg border border-zinc-300 bg-white px-4 py-3 text-base text-black shadow-sm outline-none focus:border-zinc-500 focus:ring-2 focus:ring-zinc-200 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-50 dark:focus:border-zinc-400 dark:focus:ring-zinc-700"
        />
      </form>

      {trimmed.length > 0 && (
        <div
          className="mt-2 overflow-hidden rounded-lg border border-zinc-200 bg-white shadow-sm dark:border-zinc-800 dark:bg-zinc-900"
          role="listbox"
          aria-label="검색 결과"
        >
          {isFetching && (
            <div className="px-4 py-2 text-sm text-zinc-500">검색 중…</div>
          )}
          {!isFetching && error && (
            <div className="px-4 py-2 text-sm text-red-600">
              검색을 일시적으로 사용할 수 없습니다. 잠시 후 다시 시도해 주세요.
            </div>
          )}
          {!isFetching && !error && hits.length === 0 && (
            <div className="px-4 py-2 text-sm text-zinc-500">
              결과가 없습니다.
            </div>
          )}
          {hits.map((hit) => (
            <Link
              key={hit.ticker}
              href={`/stock/${hit.ticker}`}
              className="flex items-baseline justify-between gap-4 px-4 py-2 hover:bg-zinc-50 dark:hover:bg-zinc-800"
              role="option"
              aria-selected="false"
            >
              <span className="font-semibold text-zinc-900 dark:text-zinc-50">
                {hit.ticker}
              </span>
              <span className="truncate text-sm text-zinc-600 dark:text-zinc-400">
                {hit.name}
              </span>
              <span className="shrink-0 text-xs text-zinc-400">
                {hit.exchange}
              </span>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
