'use client';

import Link from 'next/link';

import { useAuth } from '@/features/auth/auth-provider';

export function UserMenu() {
  const { user, isLoading } = useAuth();

  if (isLoading) {
    return (
      <div className="h-8 w-8 animate-pulse rounded-full bg-bg-skeleton" />
    );
  }

  if (!user) {
    return (
      <Link
        href="/auth/login"
        className="cursor-pointer rounded-lg border border-border px-3 py-1.5 text-xs font-medium text-fg-secondary transition-colors hover:bg-bg-muted"
      >
        로그인
      </Link>
    );
  }

  return (
    <Link
      href="/my"
      className="flex h-8 w-8 items-center justify-center rounded-full bg-primary text-primary-fg shadow-sm transition-opacity hover:opacity-80"
      aria-label="마이페이지"
    >
      <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor">
        <path strokeLinecap="round" strokeLinejoin="round" d="M15.75 6a3.75 3.75 0 11-7.5 0 3.75 3.75 0 017.5 0zM4.501 20.118a7.5 7.5 0 0114.998 0A17.933 17.933 0 0112 21.75c-2.676 0-5.216-.584-7.499-1.632z" />
      </svg>
    </Link>
  );
}
