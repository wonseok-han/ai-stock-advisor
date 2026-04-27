'use client';

import { Suspense, useEffect, useCallback } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';

import { useAuth } from '@/features/auth/auth-provider';
import { useBookmarks } from '@/features/bookmark/hooks/use-bookmarks';
import { useNotificationSettings } from '@/features/notification/hooks/use-notification-settings';
import { ProfileSection } from '@/features/my-page/profile-section';
import { BookmarkGrid } from '@/features/my-page/bookmark-grid';
import { NotificationSection } from '@/features/my-page/notification-section';
import { AccountSection } from '@/features/my-page/account-section';
import { cn } from '@/lib/cn';

type Tab = 'bookmarks' | 'notifications' | 'account';
const VALID_TABS: Tab[] = ['bookmarks', 'notifications', 'account'];

export default function MyPage() {
  return (
    <Suspense fallback={<MyPageSkeleton />}>
      <MyPageContent />
    </Suspense>
  );
}

function MyPageSkeleton() {
  return (
    <div className="mx-auto w-full max-w-3xl px-4 py-8 sm:px-6">
      <div className="animate-pulse rounded-2xl bg-bg-skeleton p-6">
        <div className="flex items-center gap-5">
          <div className="h-16 w-16 shrink-0 rounded-full bg-bg-muted" />
          <div className="flex-1 space-y-2">
            <div className="h-5 w-48 rounded bg-bg-muted" />
            <div className="h-4 w-32 rounded bg-bg-muted" />
          </div>
        </div>
      </div>
      <div className="mt-6 flex gap-4 border-b border-border pb-3">
        <div className="h-4 w-16 animate-pulse rounded bg-bg-skeleton" />
        <div className="h-4 w-12 animate-pulse rounded bg-bg-skeleton" />
        <div className="h-4 w-12 animate-pulse rounded bg-bg-skeleton" />
      </div>
      <div className="mt-6 grid grid-cols-2 gap-4 sm:grid-cols-3">
        {Array.from({ length: 3 }).map((_, i) => (
          <div key={i} className="h-24 animate-pulse rounded-xl bg-bg-skeleton" />
        ))}
      </div>
    </div>
  );
}

function MyPageContent() {
  const { user, isLoading } = useAuth();
  const router = useRouter();
  const searchParams = useSearchParams();
  const tabParam = searchParams.get('tab');
  const activeTab: Tab = VALID_TABS.includes(tabParam as Tab) ? (tabParam as Tab) : 'bookmarks';

  const setActiveTab = useCallback((tab: Tab) => {
    const params = new URLSearchParams(searchParams.toString());
    params.set('tab', tab);
    router.replace(`/my?${params.toString()}`, { scroll: false });
  }, [router, searchParams]);
  const { data: bookmarkData, isLoading: bookmarkLoading } = useBookmarks();
  const { data: notificationData, isLoading: notificationLoading } = useNotificationSettings();

  useEffect(() => {
    if (!isLoading && !user) {
      router.replace('/auth/login');
    }
  }, [isLoading, user, router]);

  if (isLoading || !user) {
    return (
      <div className="mx-auto w-full max-w-3xl px-4 py-8 sm:px-6">
        <div className="animate-pulse rounded-2xl bg-bg-skeleton p-6">
          <div className="flex items-center gap-5">
            <div className="h-16 w-16 shrink-0 rounded-full bg-bg-muted" />
            <div className="flex-1 space-y-2">
              <div className="h-5 w-48 rounded bg-bg-muted" />
              <div className="h-4 w-32 rounded bg-bg-muted" />
            </div>
          </div>
        </div>
        <div className="mt-6 flex gap-4 border-b border-border pb-3">
          <div className="h-4 w-16 animate-pulse rounded bg-bg-skeleton" />
          <div className="h-4 w-12 animate-pulse rounded bg-bg-skeleton" />
          <div className="h-4 w-12 animate-pulse rounded bg-bg-skeleton" />
        </div>
        <div className="mt-6 grid grid-cols-2 gap-4 sm:grid-cols-3">
          {Array.from({ length: 3 }).map((_, i) => (
            <div key={i} className="h-24 animate-pulse rounded-xl bg-bg-skeleton" />
          ))}
        </div>
      </div>
    );
  }

  const tabs: { key: Tab; label: string; count?: number; icon: React.ReactNode }[] = [
    {
      key: 'bookmarks',
      label: '북마크',
      count: bookmarkData?.total,
      icon: (
        <svg className="h-4 w-4" viewBox="0 0 24 24" fill="currentColor">
          <path d="M11.48 3.499a.562.562 0 011.04 0l2.125 5.111a.563.563 0 00.475.345l5.518.442c.499.04.701.663.321.988l-4.204 3.602a.563.563 0 00-.182.557l1.285 5.385a.562.562 0 01-.84.61l-4.725-2.885a.562.562 0 00-.586 0L6.982 20.54a.562.562 0 01-.84-.61l1.285-5.386a.562.562 0 00-.182-.557l-4.204-3.602a.562.562 0 01.321-.988l5.518-.442a.563.563 0 00.475-.345L11.48 3.5z" />
        </svg>
      ),
    },
    {
      key: 'notifications',
      label: '알림',
      count: notificationData?.length,
      icon: (
        <svg className="h-4 w-4" viewBox="0 0 24 24" fill="currentColor">
          <path d="M5.85 3.5a.75.75 0 00-1.117-1 9.719 9.719 0 00-2.348 4.876.75.75 0 001.479.248A8.219 8.219 0 015.85 3.5zM19.267 2.5a.75.75 0 10-1.118 1 8.22 8.22 0 011.987 4.124.75.75 0 001.48-.248A9.72 9.72 0 0019.266 2.5z" />
          <path fillRule="evenodd" d="M12 2.25A6.75 6.75 0 005.25 9v.75a8.217 8.217 0 01-2.119 5.52.75.75 0 00.298 1.206c1.544.57 3.16.99 4.831 1.243a3.75 3.75 0 107.48 0 24.583 24.583 0 004.83-1.244.75.75 0 00.298-1.205 8.217 8.217 0 01-2.118-5.52V9A6.75 6.75 0 0012 2.25zM9.75 18c0-.034 0-.067.002-.1a25.05 25.05 0 004.496 0l.002.1a2.25 2.25 0 01-4.5 0z" clipRule="evenodd" />
        </svg>
      ),
    },
    {
      key: 'account',
      label: '계정',
      icon: (
        <svg className="h-4 w-4" viewBox="0 0 24 24" fill="currentColor">
          <path fillRule="evenodd" d="M11.078 2.25c-.917 0-1.699.663-1.85 1.567L9.05 4.889c-.02.12-.115.26-.297.348a7.493 7.493 0 00-.986.57c-.166.115-.334.126-.45.083L6.3 5.508a1.875 1.875 0 00-2.282.819l-.922 1.597a1.875 1.875 0 00.432 2.385l.84.692c.095.078.17.229.154.43a7.598 7.598 0 000 1.139c.015.2-.059.352-.153.43l-.841.692a1.875 1.875 0 00-.432 2.385l.922 1.597a1.875 1.875 0 002.282.818l1.019-.382c.115-.043.283-.031.45.082.312.214.641.405.985.57.182.088.277.228.297.35l.178 1.071c.151.904.933 1.567 1.85 1.567h1.844c.916 0 1.699-.663 1.85-1.567l.178-1.072c.02-.12.114-.26.297-.349.344-.165.673-.356.985-.57.167-.114.335-.125.45-.082l1.02.382a1.875 1.875 0 002.28-.819l.923-1.597a1.875 1.875 0 00-.432-2.385l-.84-.692c-.095-.078-.17-.229-.154-.43a7.614 7.614 0 000-1.139c-.016-.2.059-.352.153-.43l.84-.692c.708-.582.891-1.59.433-2.385l-.922-1.597a1.875 1.875 0 00-2.282-.818l-1.02.382c-.114.043-.282.031-.449-.083a7.49 7.49 0 00-.985-.57c-.183-.087-.277-.227-.297-.348l-.179-1.072a1.875 1.875 0 00-1.85-1.567h-1.843zM12 15.75a3.75 3.75 0 100-7.5 3.75 3.75 0 000 7.5z" clipRule="evenodd" />
        </svg>
      ),
    },
  ];

  return (
    <div className="mx-auto w-full max-w-3xl px-4 py-8 sm:px-6">
      <ProfileSection user={user} />

      <div className="mt-6 flex gap-1 border-b border-border">
        {tabs.map((tab) => (
          <button
            key={tab.key}
            onClick={() => setActiveTab(tab.key)}
            className={cn(
              'relative flex cursor-pointer items-center gap-1.5 px-4 py-3 text-sm font-medium transition-colors',
              activeTab === tab.key
                ? 'text-primary'
                : 'text-fg-muted hover:text-fg-secondary',
            )}
          >
            {tab.icon}
            {tab.label}
            {tab.count != null && tab.count > 0 && (
              <span
                className={cn(
                  'ml-0.5 rounded-full px-1.5 py-0.5 text-[11px] font-semibold tabular-nums',
                  activeTab === tab.key
                    ? 'bg-primary/10 text-primary'
                    : 'bg-bg-muted text-fg-muted',
                )}
              >
                {tab.count}
              </span>
            )}
            {activeTab === tab.key && (
              <span className="absolute inset-x-0 bottom-0 h-0.5 rounded-full bg-primary" />
            )}
          </button>
        ))}
      </div>

      <div className="mt-6">
        {activeTab === 'bookmarks' && (
          bookmarkLoading ? (
            <div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
              {Array.from({ length: 6 }).map((_, i) => (
                <div key={i} className="h-24 animate-pulse rounded-xl bg-bg-skeleton" />
              ))}
            </div>
          ) : (
            <BookmarkGrid />
          )
        )}
        {activeTab === 'notifications' && (
          notificationLoading ? (
            <div className="space-y-3">
              {Array.from({ length: 3 }).map((_, i) => (
                <div key={i} className="h-16 animate-pulse rounded-xl bg-bg-skeleton" />
              ))}
            </div>
          ) : (
            <NotificationSection />
          )
        )}
        {activeTab === 'account' && <AccountSection />}
      </div>
    </div>
  );
}
