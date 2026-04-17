'use client';

import { useState } from 'react';

import {
  useDeleteNotificationSetting,
  useNotificationSettings,
} from '@/features/notification/hooks/use-notification-settings';
import { NotificationSettingModal } from '@/features/stock-detail/notification-setting-modal';
import { PushPrompt } from '@/features/notification/push-prompt';

export function NotificationSection() {
  const { data, isLoading } = useNotificationSettings();
  const deleteMutation = useDeleteNotificationSetting();
  const [editingTicker, setEditingTicker] = useState<string | null>(null);

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
              <div className="flex items-center gap-3">
                <span className="font-semibold text-zinc-900 dark:text-white">{s.ticker}</span>
                <span className="text-xs text-zinc-500">±{s.priceChangeThreshold ?? 5}%</span>
                <div className="flex gap-1.5">
                  {s.onNewNews && (
                    <span className="rounded-full bg-blue-100 px-2 py-0.5 text-xs text-blue-700 dark:bg-blue-900/30 dark:text-blue-400">
                      뉴스
                    </span>
                  )}
                  {s.onSignalChange && (
                    <span className="rounded-full bg-blue-100 px-2 py-0.5 text-xs text-blue-700 dark:bg-blue-900/30 dark:text-blue-400">
                      시그널
                    </span>
                  )}
                  {!s.enabled && (
                    <span className="rounded-full bg-zinc-100 px-2 py-0.5 text-xs text-zinc-400 dark:bg-zinc-800 dark:text-zinc-500">
                      비활성
                    </span>
                  )}
                </div>
              </div>
              <div className="flex items-center gap-1">
                <button
                  onClick={() => setEditingTicker(s.ticker)}
                  className="rounded p-1.5 text-zinc-400 transition-colors hover:bg-zinc-100 hover:text-zinc-600 dark:hover:bg-zinc-700 dark:hover:text-zinc-300"
                  aria-label={`${s.ticker} 알림 설정 편집`}
                >
                  <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M9.594 3.94c.09-.542.56-.94 1.11-.94h2.593c.55 0 1.02.398 1.11.94l.213 1.281c.063.374.313.686.645.87.074.04.147.083.22.127.325.196.72.257 1.075.124l1.217-.456a1.125 1.125 0 011.37.49l1.296 2.247a1.125 1.125 0 01-.26 1.431l-1.003.827c-.293.241-.438.613-.43.992a7.723 7.723 0 010 .255c-.008.378.137.75.43.991l1.004.827c.424.35.534.955.26 1.43l-1.298 2.247a1.125 1.125 0 01-1.369.491l-1.217-.456c-.355-.133-.75-.072-1.076.124a6.47 6.47 0 01-.22.128c-.331.183-.581.495-.644.869l-.213 1.281c-.09.543-.56.941-1.11.941h-2.594c-.55 0-1.019-.398-1.11-.94l-.213-1.281c-.062-.374-.312-.686-.644-.87a6.52 6.52 0 01-.22-.127c-.325-.196-.72-.257-1.076-.124l-1.217.456a1.125 1.125 0 01-1.369-.49l-1.297-2.247a1.125 1.125 0 01.26-1.431l1.004-.827c.292-.24.437-.613.43-.991a6.932 6.932 0 010-.255c.007-.38-.138-.751-.43-.992l-1.004-.827a1.125 1.125 0 01-.26-1.43l1.297-2.247a1.125 1.125 0 011.37-.491l1.216.456c.356.133.751.072 1.076-.124.072-.044.146-.086.22-.128.332-.183.582-.495.644-.869l.214-1.28z" />
                    <path strokeLinecap="round" strokeLinejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                  </svg>
                </button>
                <button
                  onClick={() => deleteMutation.mutate(s.ticker)}
                  disabled={deleteMutation.isPending}
                  className="rounded p-1.5 text-zinc-400 transition-colors hover:bg-red-50 hover:text-red-500 disabled:opacity-50 dark:hover:bg-red-900/20 dark:hover:text-red-400"
                  aria-label={`${s.ticker} 알림 해제`}
                >
                  <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                  </svg>
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {editingTicker && (
        <NotificationSettingModal
          ticker={editingTicker}
          onClose={() => setEditingTicker(null)}
        />
      )}
    </div>
  );
}
