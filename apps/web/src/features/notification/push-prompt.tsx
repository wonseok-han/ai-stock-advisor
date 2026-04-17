'use client';

import { usePush } from '@/features/notification/hooks/use-push';

export function PushPrompt() {
  const { supported, permission, isSubscribed, loading, subscribe, unsubscribe } = usePush();

  if (!supported) return null;
  if (permission === 'denied') {
    return (
      <p className="text-xs text-gray-400">
        브라우저 알림이 차단되어 있습니다. 브라우저 설정에서 허용해 주세요.
      </p>
    );
  }

  return (
    <div className="flex items-center gap-3">
      {isSubscribed ? (
        <button
          onClick={unsubscribe}
          disabled={loading}
          className="text-sm text-red-600 hover:underline disabled:opacity-50"
        >
          푸시 알림 해제
        </button>
      ) : (
        <button
          onClick={subscribe}
          disabled={loading}
          className="rounded-lg bg-blue-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
        >
          푸시 알림 받기
        </button>
      )}
      {loading && <span className="text-xs text-gray-400">처리 중...</span>}
    </div>
  );
}
