'use client';

import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';

import { deleteAccount } from '@/lib/api/auth';
import { useAuth } from '@/features/auth/auth-provider';
import { DeleteAccountModal } from '@/features/my-page/delete-account-modal';

export function AccountSection() {
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
      <section>
        <div className="rounded-xl border border-red-200 bg-red-50/50 p-5 dark:border-red-900/50 dark:bg-red-950/20">
          <div className="flex items-center gap-3">
            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-red-100 dark:bg-red-900/30">
              <svg className="h-5 w-5 text-danger" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126zM12 15.75h.007v.008H12v-.008z" />
              </svg>
            </div>
            <div className="min-w-0 flex-1">
              <p className="text-sm font-medium text-fg">계정 삭제</p>
              <p className="mt-0.5 text-xs text-fg-muted">
                탈퇴 시 모든 북마크와 알림 설정이 영구 삭제됩니다
              </p>
            </div>
            <button
              onClick={() => setShowDeleteModal(true)}
              className="shrink-0 cursor-pointer rounded-lg border border-red-300 px-3 py-1.5 text-sm font-medium text-danger transition-colors hover:bg-red-100 dark:border-red-800 dark:hover:bg-red-900/30"
            >
              회원 탈퇴
            </button>
          </div>
        </div>
      </section>

      <DeleteAccountModal
        open={showDeleteModal}
        onClose={() => setShowDeleteModal(false)}
        onConfirm={(reason) => deleteMutation.mutate(reason)}
        isLoading={deleteMutation.isPending}
      />
    </>
  );
}
