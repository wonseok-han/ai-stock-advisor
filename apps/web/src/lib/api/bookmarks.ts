import { apiFetch } from '@/lib/api/client';

import type { BookmarkCheckResponse, BookmarkListResponse, Bookmark } from '@/types/bookmark';

export function getBookmarks(): Promise<BookmarkListResponse> {
  return apiFetch('/api/v1/bookmarks');
}

export function addBookmark(ticker: string): Promise<Bookmark> {
  return apiFetch('/api/v1/bookmarks', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ ticker }),
  });
}

export function removeBookmark(ticker: string): Promise<void> {
  return apiFetch(`/api/v1/bookmarks/${encodeURIComponent(ticker)}`, {
    method: 'DELETE',
  });
}

export function checkBookmark(ticker: string): Promise<BookmarkCheckResponse> {
  return apiFetch(`/api/v1/bookmarks/check/${encodeURIComponent(ticker)}`);
}
