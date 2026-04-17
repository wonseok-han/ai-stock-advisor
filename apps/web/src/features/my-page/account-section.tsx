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
      <div className="flex items-center justify-center py-2">
        <button
          onClick={() => setShowDeleteModal(true)}
          className="cursor-pointer text-sm text-zinc-400 underline-offset-2 transition-colors hover:text-red-500 hover:underline dark:text-zinc-500 dark:hover:text-red-400"
        >
          회원 탈퇴
        </button>
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
