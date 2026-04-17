'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';

import { useAuth } from '@/features/auth/auth-provider';

/**
 * 헤더 우측 사용자 메뉴.
 * 비로그인: 로그인 링크. 로그인: 이메일 + 마이페이지 + 로그아웃.
 */
export function UserMenu() {
  const { user, isLoading, signOut } = useAuth();
  const router = useRouter();

  if (isLoading) {
    return (
      <div className="h-8 w-16 animate-pulse rounded bg-zinc-200 dark:bg-zinc-700" />
    );
  }

  if (!user) {
    return (
      <Link
        href="/auth/login"
        className="rounded-md border border-zinc-300 px-3 py-1.5 text-xs font-medium text-zinc-700 hover:bg-zinc-100 dark:border-zinc-700 dark:text-zinc-300 dark:hover:bg-zinc-800"
      >
        로그인
      </Link>
    );
  }

  const handleSignOut = async () => {
    await signOut();
    router.push('/');
    router.refresh();
  };

  return (
    <div className="flex items-center gap-2">
      <Link
        href="/my"
        className="text-xs text-zinc-600 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-200"
      >
        마이페이지
      </Link>
      <button
        onClick={handleSignOut}
        className="rounded-md border border-zinc-300 px-3 py-1.5 text-xs font-medium text-zinc-700 hover:bg-zinc-100 dark:border-zinc-700 dark:text-zinc-300 dark:hover:bg-zinc-800"
      >
        로그아웃
      </button>
    </div>
  );
}
