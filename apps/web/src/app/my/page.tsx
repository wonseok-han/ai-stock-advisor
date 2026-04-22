'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';

import { useAuth } from '@/features/auth/auth-provider';
import { ProfileSection } from '@/features/my-page/profile-section';
import { BookmarkGrid } from '@/features/my-page/bookmark-grid';
import { NotificationSection } from '@/features/my-page/notification-section';
import { AccountSection } from '@/features/my-page/account-section';

export default function MyPage() {
  const { user, isLoading, signOut } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (!isLoading && !user) {
      router.replace('/auth/login');
    }
  }, [isLoading, user, router]);

  if (isLoading || !user) {
    return <div className="py-12 text-center text-sm text-fg-muted">로딩 중...</div>;
  }

  return (
    <div className="mx-auto max-w-2xl space-y-6 px-4 py-6 sm:px-6">
      <h1 className="text-lg font-semibold text-fg">마이페이지</h1>

      <ProfileSection user={user} onSignOut={signOut} />

      <section>
        <h2 className="mb-3 text-sm font-medium text-fg">내 북마크</h2>
        <BookmarkGrid />
      </section>

      <section>
        <h2 className="mb-3 text-sm font-medium text-fg">알림 설정</h2>
        <NotificationSection />
      </section>

      <AccountSection />
    </div>
  );
}
