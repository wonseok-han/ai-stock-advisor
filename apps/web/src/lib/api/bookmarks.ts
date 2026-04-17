import { apiFetch } from '@/lib/api/client';

import type { BookmarkCheckResponse, BookmarkListResponse, Bookmark } from '@/types/bookmark';

export function getBookmarks(): Promise<BookmarkListResponse> {
  return apiFetch('/bookmarks');
}

export function addBookmark(ticker: string): Promise<Bookmark> {
  return apiFetch('/bookmarks', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ ticker }),
  });
}

export function removeBookmark(ticker: string): Promise<void> {
  return apiFetch(`/bookmarks/${encodeURIComponent(ticker)}`, {
    method: 'DELETE',
  });
}

export function checkBookmark(ticker: string): Promise<BookmarkCheckResponse> {
  return apiFetch(`/bookmarks/check/${encodeURIComponent(ticker)}`);
}
