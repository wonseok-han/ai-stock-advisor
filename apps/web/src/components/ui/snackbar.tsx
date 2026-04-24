'use client';

import { useEffect } from 'react';

import { useSnackbarStore } from '@/stores/use-snackbar-store';

export function Snackbar() {
  const message = useSnackbarStore((s) => s.message);
  const hide = useSnackbarStore((s) => s.hide);

  useEffect(() => {
    if (!message) return;
    const timer = setTimeout(hide, 3000);
    return () => clearTimeout(timer);
  }, [message, hide]);

  if (!message) return null;

  return (
    <div className="fixed inset-x-0 bottom-6 z-50 flex justify-center animate-slide-up">
      <div className="flex items-center gap-3 rounded-lg bg-fg px-4 py-3 text-sm font-medium text-bg shadow-lg">
        <span>{message}</span>
        <button
          onClick={hide}
          className="inline-flex h-5 w-5 shrink-0 cursor-pointer items-center justify-center rounded-full text-bg/60 transition-colors hover:text-bg"
          aria-label="닫기"
        >
          <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      </div>
    </div>
  );
}
