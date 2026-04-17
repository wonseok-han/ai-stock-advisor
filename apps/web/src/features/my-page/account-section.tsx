'use client';

export function AccountSection({ onSignOut }: { onSignOut: () => void }) {
  return (
    <div className="flex items-center justify-between rounded-lg border border-zinc-200 bg-white px-5 py-4 dark:border-zinc-800 dark:bg-zinc-900">
      <span className="text-sm text-zinc-600 dark:text-zinc-400">계정 관리</span>
      <button
        onClick={onSignOut}
        className="rounded-md border border-red-200 px-3 py-1.5 text-sm text-red-600 transition-colors hover:bg-red-50 dark:border-red-800 dark:text-red-400 dark:hover:bg-red-900/20"
      >
        로그아웃
      </button>
    </div>
  );
}
