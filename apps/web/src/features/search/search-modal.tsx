'use client';

import Link from 'next/link';
import { useEffect, useRef, useState, useCallback } from 'react';

import { useSearch } from '@/features/search/hooks/use-search';
import { cn } from '@/lib/cn';

export function SearchModal({
  open,
  onClose,
}: {
  open: boolean;
  onClose: () => void;
}) {
  const [query, setQuery] = useState('');
  const [animating, setAnimating] = useState(false);
  const [mounted, setMounted] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);
  const { data, isFetching, error } = useSearch(mounted ? query : '');
  const hits = data ?? [];
  const trimmed = query.trim();

  const [prevOpen, setPrevOpen] = useState(false);
  if (open !== prevOpen) {
    setPrevOpen(open);
    if (open) {
      setMounted(true);
      setQuery('');
    } else {
      setAnimating(false);
    }
  }

  useEffect(() => {
    if (!mounted || !open) return;

    let raf2 = 0;
    const raf1 = requestAnimationFrame(() => {
      raf2 = requestAnimationFrame(() => {
        setAnimating(true);
        inputRef.current?.focus();
      });
    });
    return () => {
      cancelAnimationFrame(raf1);
      cancelAnimationFrame(raf2);
    };
  }, [open, mounted]);

  useEffect(() => {
    if (mounted && !open) {
      const timer = setTimeout(() => setMounted(false), 200);
      return () => clearTimeout(timer);
    }
  }, [mounted, open]);

  useEffect(() => {
    if (!open) return;
    const handleKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', handleKey);
    return () => document.removeEventListener('keydown', handleKey);
  }, [open, onClose]);

  const handleNavigate = useCallback(() => {
    setQuery('');
    onClose();
  }, [onClose]);

  if (!mounted) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center pt-[15vh]">
      <div
        className={cn(
          'fixed inset-0 bg-black/50 backdrop-blur-sm transition-opacity duration-200',
          animating ? 'opacity-100' : 'opacity-0',
        )}
        onClick={onClose}
        aria-hidden="true"
      />

      <div
        className={cn(
          'relative mx-4 w-full max-w-lg overflow-hidden rounded-2xl border border-border bg-bg-surface shadow-2xl transition-all duration-200',
          animating
            ? 'translate-y-0 scale-100 opacity-100'
            : '-translate-y-4 scale-95 opacity-0',
        )}
      >
        <form
          onSubmit={(e) => {
            e.preventDefault();
            if (hits.length > 0) {
              window.location.href = `/stock/${hits[0].ticker}`;
              handleNavigate();
            }
          }}
        >
          <div className="flex items-center gap-3 border-b border-border px-4 py-3">
            <svg
              className="h-5 w-5 shrink-0 text-fg-muted"
              fill="none"
              viewBox="0 0 24 24"
              strokeWidth={2}
              stroke="currentColor"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                d="M21 21l-5.197-5.197m0 0A7.5 7.5 0 105.196 5.196a7.5 7.5 0 0010.607 10.607z"
              />
            </svg>
            <input
              ref={inputRef}
              type="text"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="종목 검색 (예: AAPL, Tesla)"
              maxLength={20}
              aria-label="종목 검색"
              className="flex-1 bg-transparent text-base text-fg outline-none placeholder:text-fg-muted"
            />
            {query.length > 0 && (
              <button
                type="button"
                onClick={() => {
                  setQuery('');
                  inputRef.current?.focus();
                }}
                className="inline-flex h-5 w-5 shrink-0 cursor-pointer items-center justify-center rounded-full text-fg-muted transition-colors hover:bg-bg-muted hover:text-fg"
                aria-label="검색어 지우기"
              >
                <svg
                  className="h-3.5 w-3.5"
                  fill="none"
                  viewBox="0 0 24 24"
                  strokeWidth={2}
                  stroke="currentColor"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    d="M6 18L18 6M6 6l12 12"
                  />
                </svg>
              </button>
            )}
            <kbd className="hidden rounded-md border border-border bg-bg-muted px-1.5 py-0.5 text-[10px] font-medium text-fg-muted sm:inline-block">
              ESC
            </kbd>
          </div>
        </form>

        <div
          className="max-h-80 overflow-y-auto"
          role="listbox"
          aria-label="검색 결과"
        >
          {trimmed.length === 0 && (
            <div className="px-4 py-8 text-center text-sm text-fg-muted">
              티커 또는 종목명을 입력하세요
            </div>
          )}

          {trimmed.length > 0 && isFetching && (
            <div className="px-4 py-3 text-sm text-fg-muted">검색 중…</div>
          )}

          {trimmed.length > 0 && !isFetching && error && (
            <div className="px-4 py-3 text-sm text-danger">
              검색을 일시적으로 사용할 수 없습니다.
            </div>
          )}

          {trimmed.length > 0 && !isFetching && !error && hits.length === 0 && (
            <div className="px-4 py-3 text-sm text-fg-muted">
              결과가 없습니다.
            </div>
          )}

          {hits.map((hit) => (
            <Link
              key={hit.ticker}
              href={`/stock/${hit.ticker}`}
              onClick={handleNavigate}
              className="flex items-center gap-3 px-4 py-3 transition-colors hover:bg-bg-muted"
              role="option"
              aria-selected="false"
            >
              <span className="min-w-[4rem] text-sm font-semibold text-fg">
                {hit.ticker}
              </span>
              <span className="flex-1 truncate text-sm text-fg-secondary">
                {hit.name}
              </span>
              <span className="shrink-0 text-xs text-fg-muted">
                {hit.exchange}
              </span>
            </Link>
          ))}
        </div>
      </div>
    </div>
  );
}
