import Link from 'next/link';

import { UserMenu } from '@/features/auth/user-menu';

export function SiteHeader() {
  return (
    <header className="border-b border-zinc-200 bg-white dark:border-zinc-800 dark:bg-zinc-900">
      <div className="mx-auto flex w-full max-w-5xl items-center justify-between px-4 py-3 sm:px-6">
        <div className="flex items-center gap-2">
          <Link
            href="/"
            className="text-sm font-semibold text-zinc-900 hover:text-zinc-600 dark:text-zinc-50 dark:hover:text-zinc-300"
          >
            AI Stock Advisor
          </Link>
          <span className="rounded-full bg-amber-100 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-amber-700 dark:bg-amber-900/40 dark:text-amber-300">
            Beta
          </span>
          <p className="hidden text-[10px] text-zinc-400 sm:block sm:text-xs">
            개발 중인 베타 서비스입니다. 쌀먹 인프라로 운영되어 응답이 많이 느릴
            수 있습니다.
          </p>
        </div>
        <UserMenu />
      </div>
    </header>
  );
}
