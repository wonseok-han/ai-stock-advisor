'use client';

import Link from 'next/link';

export function AuthGuardModal({ onClose }: { onClose: () => void }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40" onClick={onClose}>
      <div
        className="w-full max-w-sm rounded-xl bg-white p-6 shadow-xl dark:bg-gray-900"
        onClick={(e) => e.stopPropagation()}
      >
        <h2 className="text-lg font-semibold text-gray-900 dark:text-white">로그인이 필요합니다</h2>
        <p className="mt-2 text-sm text-gray-600 dark:text-gray-400">
          북마크 기능을 이용하려면 로그인해 주세요.
        </p>
        <div className="mt-5 flex gap-3">
          <Link
            href="/auth/login"
            className="flex-1 rounded-lg bg-blue-600 px-4 py-2 text-center text-sm font-medium text-white hover:bg-blue-700"
          >
            로그인
          </Link>
          <button
            onClick={onClose}
            className="flex-1 cursor-pointer rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 dark:border-gray-700 dark:text-gray-300 dark:hover:bg-gray-800"
          >
            닫기
          </button>
        </div>
      </div>
    </div>
  );
}
