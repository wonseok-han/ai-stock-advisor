'use client';

import { useMemo, useState } from 'react';

import { useAddBookmark, useBookmarkCheck } from '@/features/bookmark/hooks/use-bookmarks';
import {
  useDeleteNotificationSetting,
  useNotificationSettings,
  useUpsertNotificationSetting,
} from '@/features/notification/hooks/use-notification-settings';

interface Props {
  ticker: string;
  onClose: () => void;
}

const THRESHOLD_OPTIONS = [1, 3, 5, 10];

function NotificationSettingModalInner({
  ticker,
  onClose,
  isExisting,
  initialThreshold,
  initialOnNewNews,
  initialOnSignalChange,
}: Props & {
  isExisting: boolean;
  initialThreshold: number;
  initialOnNewNews: boolean;
  initialOnSignalChange: boolean;
}) {
  const { data: bookmarkCheck } = useBookmarkCheck(ticker);
  const upsertMutation = useUpsertNotificationSetting();
  const deleteMutation = useDeleteNotificationSetting();
  const addBookmarkMutation = useAddBookmark();

  const [threshold, setThreshold] = useState<number>(initialThreshold);
  const [onNewNews, setOnNewNews] = useState(initialOnNewNews);
  const [onSignalChange, setOnSignalChange] = useState(initialOnSignalChange);

  function handleSave() {
    // 미북마크 종목이면 자동 북마크 추가
    if (!bookmarkCheck?.bookmarked) {
      addBookmarkMutation.mutate(ticker);
    }

    upsertMutation.mutate(
      {
        ticker,
        req: {
          priceChangeThreshold: threshold,
          onNewNews,
          onSignalChange,
          enabled: true,
        },
      },
      { onSuccess: onClose },
    );
  }

  function handleDelete() {
    deleteMutation.mutate(ticker, { onSuccess: onClose });
  }

  const isPending = upsertMutation.isPending || addBookmarkMutation.isPending || deleteMutation.isPending;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50" onClick={onClose}>
      <div
        className="mx-4 w-full max-w-sm rounded-xl border border-zinc-200 bg-white p-6 shadow-xl dark:border-zinc-700 dark:bg-zinc-900"
        onClick={(e) => e.stopPropagation()}
      >
        <h3 className="text-lg font-semibold text-zinc-900 dark:text-white">
          {ticker} 알림 설정
        </h3>

        <div className="mt-5 space-y-4">
          <div>
            <label className="text-sm font-medium text-zinc-700 dark:text-zinc-300">
              가격 변동 임계치
            </label>
            <div className="mt-1.5 flex gap-2">
              {THRESHOLD_OPTIONS.map((v) => (
                <button
                  key={v}
                  onClick={() => setThreshold(v)}
                  className={`rounded-lg px-3 py-1.5 text-sm font-medium transition-colors ${
                    threshold === v
                      ? 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400'
                      : 'bg-zinc-100 text-zinc-600 hover:bg-zinc-200 dark:bg-zinc-800 dark:text-zinc-400'
                  }`}
                >
                  ±{v}%
                </button>
              ))}
            </div>
          </div>

          <ToggleRow
            label="새 뉴스 발생 시"
            checked={onNewNews}
            onChange={setOnNewNews}
          />
          <ToggleRow
            label="AI 시그널 변화 시"
            checked={onSignalChange}
            onChange={setOnSignalChange}
          />
        </div>

        {!bookmarkCheck?.bookmarked && (
          <p className="mt-3 text-xs text-zinc-500">
            알림 설정 시 자동으로 북마크에 추가됩니다.
          </p>
        )}

        <div className="mt-6 flex items-center justify-between">
          {isExisting ? (
            <button
              onClick={handleDelete}
              disabled={isPending}
              className="rounded-lg px-4 py-2 text-sm text-red-600 hover:bg-red-50 disabled:opacity-50 dark:text-red-400 dark:hover:bg-red-900/20"
            >
              알림 해제
            </button>
          ) : (
            <span />
          )}
          <div className="flex gap-3">
            <button
              onClick={onClose}
              className="rounded-lg px-4 py-2 text-sm text-zinc-600 hover:bg-zinc-100 dark:text-zinc-400 dark:hover:bg-zinc-800"
            >
              취소
            </button>
            <button
              onClick={handleSave}
              disabled={isPending}
              className="rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
            >
              {isPending ? '저장 중...' : '저장'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

export function NotificationSettingModal({ ticker, onClose }: Props) {
  const { data: settings } = useNotificationSettings();
  const existing = useMemo(
    () => settings?.find((s) => s.ticker === ticker),
    [settings, ticker],
  );

  return (
    <NotificationSettingModalInner
      key={existing ? 'loaded' : 'default'}
      ticker={ticker}
      onClose={onClose}
      isExisting={!!existing}
      initialThreshold={existing?.priceChangeThreshold ?? 5}
      initialOnNewNews={existing?.onNewNews ?? true}
      initialOnSignalChange={existing?.onSignalChange ?? true}
    />
  );
}

function ToggleRow({
  label,
  checked,
  onChange,
}: {
  label: string;
  checked: boolean;
  onChange: (v: boolean) => void;
}) {
  return (
    <div className="flex items-center justify-between">
      <span className="text-sm text-zinc-700 dark:text-zinc-300">{label}</span>
      <button
        onClick={() => onChange(!checked)}
        className={`relative h-6 w-11 rounded-full transition-colors ${
          checked ? 'bg-blue-600' : 'bg-zinc-300 dark:bg-zinc-600'
        }`}
      >
        <span
          className={`absolute left-0.5 top-0.5 h-5 w-5 rounded-full bg-white shadow transition-transform ${
            checked ? 'translate-x-5' : 'translate-x-0'
          }`}
        />
      </button>
    </div>
  );
}
