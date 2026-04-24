'use client';

import { useMemo, useState } from 'react';

import { useAuth } from '@/features/auth/auth-provider';
import { useNotificationSettings } from '@/features/notification/hooks/use-notification-settings';
import { AuthGuardModal } from '@/features/auth/auth-guard-modal';
import { NotificationSettingModal } from '@/features/stock-detail/notification-setting-modal';

export function NotificationButton({ ticker }: { ticker: string }) {
  const { user } = useAuth();
  const { data: settings } = useNotificationSettings();
  const [showAuthModal, setShowAuthModal] = useState(false);
  const [showSettingModal, setShowSettingModal] = useState(false);

  const isActive = useMemo(
    () => settings?.some((s) => s.ticker === ticker && s.enabled) ?? false,
    [settings, ticker],
  );

  function handleClick() {
    if (!user) {
      setShowAuthModal(true);
      return;
    }
    setShowSettingModal(true);
  }

  return (
    <>
      <button
        onClick={handleClick}
        className={`inline-flex cursor-pointer items-center justify-center rounded-lg px-2 py-1.5 transition-colors ${
          isActive
            ? 'bg-blue-100 text-blue-700 hover:bg-blue-200 dark:bg-blue-900/30 dark:text-blue-400'
            : 'bg-bg-muted text-fg-secondary hover:bg-bg-muted'
        }`}
        aria-label={isActive ? '알림 설정됨 (편집)' : '알림 설정'}
        title={isActive ? '알림 설정됨 (편집)' : '알림 설정'}
      >
        <svg
          className="h-4 w-4"
          fill="currentColor"
          viewBox="0 0 24 24"
          strokeWidth={1.5}
          stroke="currentColor"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            d="M14.857 17.082a23.848 23.848 0 005.454-1.31A8.967 8.967 0 0118 9.75v-.7V9A6 6 0 006 9v.75a8.967 8.967 0 01-2.312 6.022c1.733.64 3.56 1.085 5.455 1.31m5.714 0a24.255 24.255 0 01-5.714 0m5.714 0a3 3 0 11-5.714 0"
          />
        </svg>
      </button>
      {showAuthModal && <AuthGuardModal onClose={() => setShowAuthModal(false)} />}
      {showSettingModal && (
        <NotificationSettingModal ticker={ticker} onClose={() => setShowSettingModal(false)} />
      )}
    </>
  );
}
