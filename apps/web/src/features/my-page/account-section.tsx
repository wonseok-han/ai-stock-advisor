'use client';

import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';

import { deleteAccount } from '@/lib/api/auth';
import { useAuth } from '@/features/auth/auth-provider';
import { DeleteAccountModal } from '@/features/my-page/delete-account-modal';

export function AccountSection({ onSignOut }: { onSignOut: () => void }) {
  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const { signOut } = useAuth();

  const deleteMutation = useMutation({
    mutationFn: (reason?: string) => deleteAccount(reason),
    onSuccess: async () => {
      await signOut();
    },
  });

  return (
    <>
      <div className="flex items-center justify-between rounded-lg border border-zinc-200 bg-white px-5 py-4 dark:border-zinc-800 dark:bg-zinc-900">
        <span className="text-sm text-zinc-600 dark:text-zinc-400">계정 관리</span>
        <div className="flex gap-2">
          <button
            onClick={() => setShowDeleteModal(true)}
            className="rounded-md px-3 py-1.5 text-sm text-zinc-500 transition-colors hover:text-red-600 dark:text-zinc-500 dark:hover:text-red-400"
          >
            회원 탈퇴
          </button>
          <button
            onClick={onSignOut}
            className="rounded-md border border-red-200 px-3 py-1.5 text-sm text-red-600 transition-colors hover:bg-red-50 dark:border-red-800 dark:text-red-400 dark:hover:bg-red-900/20"
          >
            로그아웃
          </button>
        </div>
      </div>

      <DeleteAccountModal
        open={showDeleteModal}
        onClose={() => setShowDeleteModal(false)}
        onConfirm={(reason) => deleteMutation.mutate(reason)}
        isLoading={deleteMutation.isPending}
      />
    </>
  );
}
