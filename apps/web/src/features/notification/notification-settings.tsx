'use client';

import { useNotificationSettings, useUpsertNotificationSetting } from '@/features/notification/hooks/use-notification-settings';

import type { NotificationSetting } from '@/types/notification';

export function NotificationSettings() {
  const { data, isLoading } = useNotificationSettings();
  const upsertMutation = useUpsertNotificationSetting();

  if (isLoading) {
    return <p className="text-sm text-gray-500">로딩 중...</p>;
  }

  if (!data || data.length === 0) {
    return (
      <div className="rounded-lg border border-dashed border-gray-300 p-6 text-center dark:border-gray-700">
        <p className="text-sm text-gray-500 dark:text-gray-400">알림 설정이 없습니다.</p>
        <p className="mt-1 text-xs text-gray-400 dark:text-gray-500">
          종목 상세에서 북마크 후 알림을 설정할 수 있습니다.
        </p>
      </div>
    );
  }

  function handleToggle(setting: NotificationSetting, field: 'onNewNews' | 'onSignalChange' | 'enabled') {
    upsertMutation.mutate({
      ticker: setting.ticker,
      req: {
        priceChangeThreshold: setting.priceChangeThreshold,
        onNewNews: field === 'onNewNews' ? !setting.onNewNews : setting.onNewNews,
        onSignalChange: field === 'onSignalChange' ? !setting.onSignalChange : setting.onSignalChange,
        enabled: field === 'enabled' ? !setting.enabled : setting.enabled,
      },
    });
  }

  return (
    <div className="space-y-2">
      {data.map((s) => (
        <div
          key={s.ticker}
          className="flex items-center justify-between rounded-lg border border-gray-200 px-4 py-3 dark:border-gray-700"
        >
          <div>
            <span className="font-semibold text-gray-900 dark:text-white">{s.ticker}</span>
            {s.priceChangeThreshold != null && (
              <span className="ml-2 text-xs text-gray-500">가격 ±{s.priceChangeThreshold}%</span>
            )}
          </div>
          <div className="flex items-center gap-3">
            <ToggleChip
              label="뉴스"
              active={s.onNewNews}
              onClick={() => handleToggle(s, 'onNewNews')}
            />
            <ToggleChip
              label="시그널"
              active={s.onSignalChange}
              onClick={() => handleToggle(s, 'onSignalChange')}
            />
            <ToggleChip
              label="활성"
              active={s.enabled}
              onClick={() => handleToggle(s, 'enabled')}
            />
          </div>
        </div>
      ))}
    </div>
  );
}

function ToggleChip({ label, active, onClick }: { label: string; active: boolean; onClick: () => void }) {
  return (
    <button
      onClick={onClick}
      className={`rounded-full px-2 py-0.5 text-xs font-medium transition-colors ${
        active
          ? 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400'
          : 'bg-gray-100 text-gray-400 dark:bg-gray-800 dark:text-gray-500'
      }`}
    >
      {label}
    </button>
  );
}
