'use client';

import Link from 'next/link';
import { useState } from 'react';

import { useSearch } from '@/features/search/hooks/use-search';
import { cn } from '@/lib/cn';

interface SearchBoxProps {
  className?: string;
  compact?: boolean;
}

export function SearchBox({ className, compact = false }: SearchBoxProps) {
  const [query, setQuery] = useState('');
  const { data, isFetching, error } = useSearch(query);
  const hits = data ?? [];
  const trimmed = query.trim();

  return (
    <div className={cn('relative w-full', compact ? 'max-w-sm' : 'max-w-xl', className)}>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          if (hits.length > 0) {
            window.location.href = `/stock/${hits[0].ticker}`;
          }
        }}
      >
        <div className={cn('search-glow relative rounded-xl transition-shadow', !compact && 'brand-glow-strong')}>
          <svg
            className={cn(
              'pointer-events-none absolute top-1/2 -translate-y-1/2 text-fg-muted',
              compact ? 'left-3 h-3.5 w-3.5' : 'left-4 h-4 w-4',
            )}
            fill="none"
            viewBox="0 0 24 24"
            strokeWidth={2}
            stroke="currentColor"
          >
            <path strokeLinecap="round" strokeLinejoin="round" d="M21 21l-5.197-5.197m0 0A7.5 7.5 0 105.196 5.196a7.5 7.5 0 0010.607 10.607z" />
          </svg>
          <input
            type="search"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={compact ? '종목 검색…' : '종목 검색 (예: AAPL, Tesla)'}
            maxLength={20}
            aria-label="종목 검색"
            className={cn(
              'w-full rounded-xl border border-border bg-bg-surface text-fg outline-none transition-colors placeholder:text-fg-muted',
              'focus:border-primary focus:ring-1 focus:ring-primary',
              compact ? 'py-1.5 pl-8 pr-3 text-sm' : 'py-3 pl-11 pr-4 text-base',
            )}
          />
        </div>
      </form>

      {trimmed.length > 0 && (
        <div
          className="absolute left-0 right-0 top-full z-50 mt-2 overflow-hidden rounded-xl border border-border bg-bg-surface shadow-lg"
          role="listbox"
          aria-label="검색 결과"
        >
          {isFetching && (
            <div className="px-4 py-2 text-sm text-fg-muted">검색 중…</div>
          )}
          {!isFetching && error && (
            <div className="px-4 py-2 text-sm text-danger">
              검색을 일시적으로 사용할 수 없습니다.
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
              className="flex items-baseline justify-between gap-4 px-4 py-2.5 transition-colors hover:bg-bg-muted"
              role="option"
              aria-selected="false"
            >
              <span className="font-semibold text-fg">{hit.ticker}</span>
              <span className="truncate text-sm text-fg-secondary">{hit.name}</span>
              <span className="shrink-0 text-xs text-fg-muted">{hit.exchange}</span>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
