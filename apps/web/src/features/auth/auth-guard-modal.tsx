'use client';

import Link from 'next/link';

export function AuthGuardModal({ onClose }: { onClose: () => void }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40" onClick={onClose}>
      <div
        className="w-full max-w-sm rounded-xl bg-bg-surface p-6 shadow-xl"
        onClick={(e) => e.stopPropagation()}
      >
        <h2 className="text-lg font-semibold text-fg">로그인이 필요합니다</h2>
        <p className="mt-2 text-sm text-fg-secondary">
          북마크 기능을 이용하려면 로그인해 주세요.
        </p>
        <div className="mt-5 flex gap-3">
          <Link
            href="/auth/login"
            className="flex-1 rounded-lg bg-primary px-4 py-2 text-center text-sm font-medium text-primary-fg hover:bg-primary-hover"
          >
            로그인
          </Link>
          <button
            onClick={onClose}
            className="flex-1 cursor-pointer rounded-lg border border-border px-4 py-2 text-sm font-medium text-fg-secondary hover:bg-bg-muted"
          >
            닫기
          </button>
        </div>
      </div>
    </div>
  );
}
