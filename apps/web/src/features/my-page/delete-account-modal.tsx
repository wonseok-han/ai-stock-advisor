'use client';

import { useState } from 'react';

interface DeleteAccountModalProps {
  open: boolean;
  onClose: () => void;
  onConfirm: (reason?: string) => void;
  isLoading: boolean;
}

export function DeleteAccountModal({ open, onClose, onConfirm, isLoading }: DeleteAccountModalProps) {
  const [reason, setReason] = useState('');

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50" onClick={onClose}>
      <div
        className="mx-4 w-full max-w-md rounded-xl bg-white p-6 dark:bg-zinc-900"
        onClick={(e) => e.stopPropagation()}
      >
        <h2 className="text-lg font-semibold text-zinc-900 dark:text-zinc-100">
          회원 탈퇴
        </h2>

        <div className="mt-3 space-y-2 text-sm text-zinc-600 dark:text-zinc-400">
          <p>정말 탈퇴하시겠습니까?</p>
          <ul className="list-disc space-y-1 pl-5">
            <li>북마크, 알림 설정 등 모든 데이터가 삭제됩니다.</li>
            <li>개인정보는 관련 법령에 따라 2년간 보관 후 파기됩니다.</li>
            <li>탈퇴 후 동일 이메일로 재가입 시 계정을 복구할 수 있습니다.</li>
          </ul>
        </div>

        <div className="mt-4">
          <label className="block text-sm font-medium text-zinc-700 dark:text-zinc-300">
            탈퇴 사유 (선택)
          </label>
          <textarea
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            maxLength={100}
            rows={2}
            placeholder="서비스 개선에 참고하겠습니다."
            className="mt-1 w-full rounded-md border border-zinc-300 bg-white px-3 py-2 text-sm text-zinc-900 placeholder:text-zinc-400 focus:border-red-500 focus:outline-none focus:ring-1 focus:ring-red-500 dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-100 dark:placeholder:text-zinc-500"
          />
        </div>

        <div className="mt-5 flex justify-end gap-2">
          <button
            onClick={onClose}
            disabled={isLoading}
            className="rounded-md border border-zinc-300 px-4 py-2 text-sm text-zinc-700 transition-colors hover:bg-zinc-100 dark:border-zinc-700 dark:text-zinc-300 dark:hover:bg-zinc-800"
          >
            취소
          </button>
          <button
            onClick={() => onConfirm(reason.trim() || undefined)}
            disabled={isLoading}
            className="rounded-md bg-red-600 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-red-700 disabled:opacity-50"
          >
            {isLoading ? '처리 중...' : '탈퇴하기'}
          </button>
        </div>
      </div>
    </div>
  );
}
