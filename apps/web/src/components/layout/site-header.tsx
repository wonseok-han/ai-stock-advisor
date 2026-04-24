'use client';

import Link from 'next/link';
import { useCallback, useEffect, useState, useSyncExternalStore } from 'react';

import { Logo } from '@/components/brand/logo';
import { ThemeSwitcher } from '@/components/theme/theme-switcher';
import { SearchModal } from '@/features/search/search-modal';
import { UserMenu } from '@/features/auth/user-menu';
import { modKey } from '@/lib/platform';

const noopSubscribe = () => () => {};
const getShortcut = () => `${modKey()}K`;
const getServerShortcut = () => '';

export function SiteHeader() {
  const [searchOpen, setSearchOpen] = useState(false);
  const shortcut = useSyncExternalStore(noopSubscribe, getShortcut, getServerShortcut);

  const openSearch = useCallback(() => setSearchOpen(true), []);
  const closeSearch = useCallback(() => setSearchOpen(false), []);

  useEffect(() => {
    const handleKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
        e.preventDefault();
        setSearchOpen((prev) => !prev);
      }
    };
    document.addEventListener('keydown', handleKey);
    return () => document.removeEventListener('keydown', handleKey);
  }, []);

  return (
    <>
      <div className="bg-amber-50 text-center text-[11px] leading-relaxed text-amber-800 dark:bg-amber-900/30 dark:text-amber-200 sm:text-xs">
          <p className="px-4 py-1.5">
            개발 중인 베타 서비스입니다. 쌀먹 인프라로 운영되어 응답이 많이 느릴 수 있습니다.
          </p>
        </div>
      <header className="glass-header sticky top-0 z-40 border-b border-border">
        <div className="mx-auto flex w-full max-w-7xl items-center justify-between px-4 py-2.5 sm:px-6">
          <div className="flex items-center gap-2">
            <Link href="/" className="shrink-0 transition-opacity hover:opacity-80">
              <Logo size="sm" />
            </Link>
            <span className="rounded-full bg-amber-100 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-amber-700 dark:bg-amber-900/40 dark:text-amber-300">
              Beta
            </span>
          </div>

          <div className="flex items-center gap-3">
            <button
              onClick={openSearch}
              aria-label={`종목 검색 (${shortcut})`}
              className="inline-flex h-7 w-7 cursor-pointer items-center justify-center rounded-lg text-fg-muted transition-colors hover:bg-bg-muted hover:text-fg"
            >
              <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" d="M21 21l-5.197-5.197m0 0A7.5 7.5 0 105.196 5.196a7.5 7.5 0 0010.607 10.607z" />
              </svg>
            </button>
            <ThemeSwitcher />
            <UserMenu />
          </div>
        </div>
      </header>

      <SearchModal open={searchOpen} onClose={closeSearch} />
    </>
  );
}
