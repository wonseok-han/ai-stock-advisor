'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { addBookmark, checkBookmark, getBookmarks, removeBookmark } from '@/lib/api/bookmarks';
import { useAuth } from '@/features/auth/auth-provider';

import type { BookmarkListResponse, BookmarkCheckResponse } from '@/types/bookmark';

export function useBookmarks() {
  const { user } = useAuth();
  return useQuery<BookmarkListResponse>({
    queryKey: ['bookmarks'],
    queryFn: getBookmarks,
    enabled: !!user,
  });
}

export function useBookmarkCheck(ticker: string) {
  const { user } = useAuth();
  return useQuery<BookmarkCheckResponse>({
    queryKey: ['bookmark-check', ticker],
    queryFn: () => checkBookmark(ticker),
    enabled: !!user && !!ticker,
  });
}

export function useAddBookmark() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (ticker: string) => addBookmark(ticker),
    onSuccess: (_data, ticker) => {
      qc.invalidateQueries({ queryKey: ['bookmarks'] });
      qc.invalidateQueries({ queryKey: ['bookmark-check', ticker] });
    },
  });
}

export function useRemoveBookmark() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (ticker: string) => removeBookmark(ticker),
    onSuccess: (_data, ticker) => {
      qc.invalidateQueries({ queryKey: ['bookmarks'] });
      qc.invalidateQueries({ queryKey: ['bookmark-check', ticker] });
    },
  });
}
