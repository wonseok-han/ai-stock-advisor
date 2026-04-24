'use client';

import type { User } from '@supabase/supabase-js';

export function ProfileSection({ user }: { user: User }) {
  const email = user.email ?? '';
  const createdAt = user.created_at
    ? new Date(user.created_at).toLocaleDateString('ko-KR', {
        year: 'numeric',
        month: 'long',
        day: 'numeric',
      })
    : null;

  return (
    <div className="rounded-2xl bg-gradient-to-br from-primary/5 to-primary/10 p-6 dark:from-primary/10 dark:to-primary/5">
      <div className="flex items-center gap-5">
        <div className="flex h-16 w-16 shrink-0 items-center justify-center rounded-full bg-primary text-primary-fg shadow-md">
          <svg className="h-8 w-8" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" d="M15.75 6a3.75 3.75 0 11-7.5 0 3.75 3.75 0 017.5 0zM4.501 20.118a7.5 7.5 0 0114.998 0A17.933 17.933 0 0112 21.75c-2.676 0-5.216-.584-7.499-1.632z" />
          </svg>
        </div>
        <div className="min-w-0 flex-1">
          <p className="truncate text-lg font-semibold text-fg">
            {email}
          </p>
          {createdAt && (
            <p className="mt-1 text-sm text-fg-muted">
              {createdAt}부터 함께하고 있어요
            </p>
          )}
        </div>
      </div>
    </div>
  );
}
