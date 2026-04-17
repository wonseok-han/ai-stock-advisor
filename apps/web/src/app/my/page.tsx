'use client';

import { useRouter } from 'next/navigation';

import { useAuth } from '@/features/auth/auth-provider';
import { BookmarkList } from '@/features/bookmark/bookmark-list';
import { NotificationSettings } from '@/features/notification/notification-settings';
import { PushPrompt } from '@/features/notification/push-prompt';

export default function MyPage() {
  const { user, isLoading, signOut } = useAuth();
  const router = useRouter();

  if (isLoading) {
    return <div className="py-12 text-center text-sm text-gray-500">로딩 중...</div>;
  }

  if (!user) {
    router.replace('/auth/login');
    return null;
  }

  return (
    <div className="mx-auto max-w-2xl space-y-8 py-6">
      <section>
        <h1 className="text-xl font-semibold text-gray-900 dark:text-white">마이페이지</h1>
        <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">{user.email}</p>
      </section>

      <section>
        <h2 className="mb-3 text-lg font-medium text-gray-900 dark:text-white">내 북마크</h2>
        <BookmarkList />
      </section>

      <section>
        <h2 className="mb-3 text-lg font-medium text-gray-900 dark:text-white">알림 설정</h2>
        <PushPrompt />
        <div className="mt-4">
          <NotificationSettings />
        </div>
      </section>

      <section className="border-t border-gray-200 pt-6 dark:border-gray-700">
        <button
          onClick={signOut}
          className="text-sm text-red-600 hover:underline"
        >
          로그아웃
        </button>
      </section>
    </div>
  );
}
