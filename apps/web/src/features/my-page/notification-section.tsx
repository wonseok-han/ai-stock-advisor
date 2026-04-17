'use client';

import {
  useNotificationSettings,
  useUpsertNotificationSetting,
} from '@/features/notification/hooks/use-notification-settings';
import { PushPrompt } from '@/features/notification/push-prompt';

import type { NotificationSetting } from '@/types/notification';

export function NotificationSection() {
  const { data, isLoading } = useNotificationSettings();
  const upsertMutation = useUpsertNotificationSetting();

  function handleToggle(
    setting: NotificationSetting,
    field: 'onNewNews' | 'onSignalChange' | 'enabled',
  ) {
    upsertMutation.mutate({
      ticker: setting.ticker,
      req: {
        priceChangeThreshold: setting.priceChangeThreshold,
        onNewNews: field === 'onNewNews' ? !setting.onNewNews : setting.onNewNews,
        onSignalChange:
          field === 'onSignalChange' ? !setting.onSignalChange : setting.onSignalChange,
        enabled: field === 'enabled' ? !setting.enabled : setting.enabled,
      },
    });
  }

  return (
    <div className="space-y-4">
      <PushPrompt />

      {isLoading && <p className="text-sm text-zinc-500">로딩 중...</p>}

      {!isLoading && (!data || data.length === 0) && (
        <div className="rounded-lg border border-dashed border-zinc-300 p-6 text-center dark:border-zinc-700">
          <svg
            className="mx-auto h-10 w-10 text-zinc-300 dark:text-zinc-600"
            fill="none"
            viewBox="0 0 24 24"
            strokeWidth={1}
            stroke="currentColor"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              d="M14.857 17.082a23.848 23.848 0 005.454-1.31A8.967 8.967 0 0118 9.75v-.7V9A6 6 0 006 9v.75a8.967 8.967 0 01-2.312 6.022c1.733.64 3.56 1.085 5.455 1.31m5.714 0a24.255 24.255 0 01-5.714 0m5.714 0a3 3 0 11-5.714 0"
            />
          </svg>
          <p className="mt-3 text-sm text-zinc-500 dark:text-zinc-400">
            알림 설정이 없습니다.
          </p>
          <p className="mt-1 text-xs text-zinc-400 dark:text-zinc-500">
            종목 상세에서 알림 버튼을 눌러 설정할 수 있습니다.
          </p>
        </div>
      )}

      {data && data.length > 0 && (
        <div className="space-y-2">
          {data.map((s) => (
            <div
              key={s.ticker}
              className="flex items-center justify-between rounded-lg border border-zinc-200 px-4 py-3 dark:border-zinc-700"
            >
              <div>
                <span className="font-semibold text-zinc-900 dark:text-white">{s.ticker}</span>
                {s.priceChangeThreshold != null && (
                  <span className="ml-2 text-xs text-zinc-500">
                    가격 ±{s.priceChangeThreshold}%
                  </span>
                )}
              </div>
              <div className="flex items-center gap-2">
                <ToggleChip label="뉴스" active={s.onNewNews} onClick={() => handleToggle(s, 'onNewNews')} />
                <ToggleChip label="시그널" active={s.onSignalChange} onClick={() => handleToggle(s, 'onSignalChange')} />
                <ToggleChip label="활성" active={s.enabled} onClick={() => handleToggle(s, 'enabled')} />
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function ToggleChip({
  label,
  active,
  onClick,
}: {
  label: string;
  active: boolean;
  onClick: () => void;
}) {
  return (
    <button
      onClick={onClick}
      className={`rounded-full px-2.5 py-0.5 text-xs font-medium transition-colors ${
        active
          ? 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400'
          : 'bg-zinc-100 text-zinc-400 dark:bg-zinc-800 dark:text-zinc-500'
      }`}
    >
      {label}
    </button>
  );
}
