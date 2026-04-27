'use client';

import { useState } from 'react';

import { useAuth } from '@/features/auth/auth-provider';
import { useBookmarkCheck, useAddBookmark, useRemoveBookmark } from '@/features/bookmark/hooks/use-bookmarks';
import { AuthGuardModal } from '@/features/auth/auth-guard-modal';
import { useSnackbarStore } from '@/stores/use-snackbar-store';

export function BookmarkButton({ ticker }: { ticker: string }) {
  const { user } = useAuth();
  const { data } = useBookmarkCheck(ticker);
  const addMutation = useAddBookmark();
  const removeMutation = useRemoveBookmark();
  const [showAuthModal, setShowAuthModal] = useState(false);
  const showSnackbar = useSnackbarStore((s) => s.show);

  const isBookmarked = data?.bookmarked ?? false;
  const isPending = addMutation.isPending || removeMutation.isPending;

  function handleClick() {
    if (!user) {
      setShowAuthModal(true);
      return;
    }
    if (isBookmarked) {
      removeMutation.mutate(ticker, {
        onSuccess: () => showSnackbar('북마크를 해제했습니다'),
      });
    } else {
      addMutation.mutate(ticker, {
        onSuccess: () => showSnackbar('북마크에 추가했습니다'),
      });
    }
  }

  return (
    <>
      <button
        onClick={handleClick}
        disabled={isPending}
        className={`inline-flex cursor-pointer items-center justify-center rounded-lg px-2 py-1.5 text-base leading-none transition-colors ${
          isBookmarked
            ? 'bg-yellow-100 text-yellow-700 hover:bg-yellow-200 dark:bg-yellow-900/30 dark:text-yellow-400'
            : 'bg-bg-muted text-fg-secondary hover:bg-bg-muted'
        } disabled:opacity-50`}
        aria-label={isBookmarked ? '북마크 해제' : '북마크 추가'}
        title={isBookmarked ? '북마크 해제' : '북마크 추가'}
      >
        {isBookmarked ? '★' : '☆'}
      </button>
      {showAuthModal && <AuthGuardModal onClose={() => setShowAuthModal(false)} />}
    </>
  );
}
