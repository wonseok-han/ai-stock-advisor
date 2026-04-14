import { apiFetch } from '@/lib/api/client';

import type { NewsItem } from '@/types/news';

export function getNews(ticker: string, limit = 5): Promise<NewsItem[]> {
  const t = encodeURIComponent(ticker);
  return apiFetch<NewsItem[]>(`/stocks/${t}/news?limit=${limit}`);
}
