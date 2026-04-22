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
      <div className="h-8 w-16 animate-pulse rounded bg-bg-muted" />
    );
  }

  if (!user) {
    return (
      <Link
        href="/auth/login"
        className="cursor-pointer rounded-md border border-border px-3 py-1.5 text-xs font-medium text-fg-secondary hover:bg-bg-muted"
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
        className="text-xs text-fg-secondary hover:text-fg"
      >
        마이페이지
      </Link>
      <button
        onClick={handleSignOut}
        className="cursor-pointer rounded-md border border-border px-3 py-1.5 text-xs font-medium text-fg-secondary hover:bg-bg-muted"
      >
        로그아웃
      </button>
    </div>
  );
}
