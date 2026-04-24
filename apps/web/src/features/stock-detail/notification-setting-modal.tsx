'use client';

import { useMemo, useState } from 'react';

import { useAddBookmark, useBookmarkCheck } from '@/features/bookmark/hooks/use-bookmarks';
import {
  useDeleteNotificationSetting,
  useNotificationSettings,
  useUpsertNotificationSetting,
} from '@/features/notification/hooks/use-notification-settings';
import { useSnackbarStore } from '@/stores/use-snackbar-store';

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
}: Props & {
  isExisting: boolean;
  initialThreshold: number;
  initialOnNewNews: boolean;
}) {
  const { data: bookmarkCheck } = useBookmarkCheck(ticker);
  const upsertMutation = useUpsertNotificationSetting();
  const deleteMutation = useDeleteNotificationSetting();
  const addBookmarkMutation = useAddBookmark();

  const showSnackbar = useSnackbarStore((s) => s.show);
  const [threshold, setThreshold] = useState<number>(initialThreshold);
  const [onNewNews, setOnNewNews] = useState(initialOnNewNews);

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
          enabled: true,
        },
      },
      { onSuccess: () => { showSnackbar('알림이 설정되었습니다'); onClose(); } },
    );
  }

  function handleDelete() {
    deleteMutation.mutate(ticker, { onSuccess: () => { showSnackbar('알림이 해제되었습니다'); onClose(); } });
  }

  const isPending = upsertMutation.isPending || addBookmarkMutation.isPending || deleteMutation.isPending;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50" onClick={onClose}>
      <div
        className="mx-4 w-full max-w-sm rounded-xl border border-border bg-bg-surface p-6 shadow-xl"
        onClick={(e) => e.stopPropagation()}
      >
        <h3 className="text-lg font-semibold text-fg">
          {ticker} 알림 설정
        </h3>

        <div className="mt-5 space-y-4">
          <div>
            <label className="text-sm font-medium text-fg-secondary">
              가격 변동 임계치
            </label>
            <div className="mt-1.5 flex gap-2">
              {THRESHOLD_OPTIONS.map((v) => (
                <button
                  key={v}
                  onClick={() => setThreshold(v)}
                  className={`cursor-pointer rounded-lg px-3 py-1.5 text-sm font-medium transition-colors ${
                    threshold === v
                      ? 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400'
                      : 'bg-bg-muted text-fg-secondary hover:bg-bg-muted'
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
        </div>

        {!bookmarkCheck?.bookmarked && (
          <p className="mt-3 text-xs text-fg-muted">
            알림 설정 시 자동으로 북마크에 추가됩니다.
          </p>
        )}

        <div className="mt-6 flex items-center justify-between">
          {isExisting ? (
            <button
              onClick={handleDelete}
              disabled={isPending}
              className="cursor-pointer rounded-lg px-4 py-2 text-sm text-danger hover:bg-red-50 disabled:opacity-50 dark:hover:bg-red-900/20"
            >
              알림 해제
            </button>
          ) : (
            <span />
          )}
          <div className="flex gap-3">
            <button
              onClick={onClose}
              className="cursor-pointer rounded-lg px-4 py-2 text-sm text-fg-secondary hover:bg-bg-muted"
            >
              취소
            </button>
            <button
              onClick={handleSave}
              disabled={isPending}
              className="cursor-pointer rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
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
      <span className="text-sm text-fg-secondary">{label}</span>
      <button
        onClick={() => onChange(!checked)}
        className={`relative cursor-pointer h-6 w-11 rounded-full transition-colors ${
          checked ? 'bg-blue-600' : 'bg-border'
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
