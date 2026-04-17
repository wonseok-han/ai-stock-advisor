'use client';

import type { User } from '@supabase/supabase-js';

export function ProfileSection({ user, onSignOut }: { user: User; onSignOut: () => void }) {
  const email = user.email ?? '';
  const initial = email.charAt(0).toUpperCase();
  const createdAt = user.created_at
    ? new Date(user.created_at).toLocaleDateString('ko-KR', {
        year: 'numeric',
        month: 'long',
        day: 'numeric',
      })
    : null;

  return (
    <div className="flex items-center gap-4 rounded-lg border border-zinc-200 bg-white p-5 dark:border-zinc-800 dark:bg-zinc-900">
      <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-full bg-blue-100 text-xl font-bold text-blue-600 dark:bg-blue-900/30 dark:text-blue-400">
        {initial}
      </div>
      <div className="min-w-0 flex-1">
        <p className="truncate text-base font-medium text-zinc-900 dark:text-zinc-100">
          {email}
        </p>
        {createdAt && (
          <p className="mt-0.5 text-sm text-zinc-500 dark:text-zinc-400">
            가입일: {createdAt}
          </p>
        )}
      </div>
      <button
        onClick={onSignOut}
        className="shrink-0 rounded-md border border-red-200 px-3 py-1.5 text-sm text-red-600 transition-colors hover:bg-red-50 dark:border-red-800 dark:text-red-400 dark:hover:bg-red-900/20"
      >
        로그아웃
      </button>
    </div>
  );
}
