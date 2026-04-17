'use client';

import { useRouter } from 'next/navigation';

import { useAuth } from '@/features/auth/auth-provider';
import { ProfileSection } from '@/features/my-page/profile-section';
import { BookmarkGrid } from '@/features/my-page/bookmark-grid';
import { NotificationSection } from '@/features/my-page/notification-section';
import { AccountSection } from '@/features/my-page/account-section';

export default function MyPage() {
  const { user, isLoading, signOut } = useAuth();
  const router = useRouter();

  if (isLoading) {
    return <div className="py-12 text-center text-sm text-zinc-500">로딩 중...</div>;
  }

  if (!user) {
    router.replace('/auth/login');
    return null;
  }

  return (
    <div className="mx-auto max-w-2xl space-y-6 py-6">
      <h1 className="text-xl font-semibold text-zinc-900 dark:text-white">마이페이지</h1>

      <ProfileSection user={user} />

      <section>
        <h2 className="mb-3 text-base font-medium text-zinc-900 dark:text-white">
          내 북마크
        </h2>
        <BookmarkGrid />
      </section>

      <section>
        <h2 className="mb-3 text-base font-medium text-zinc-900 dark:text-white">
          알림 설정
        </h2>
        <NotificationSection />
      </section>

      <AccountSection onSignOut={signOut} />
    </div>
  );
}
