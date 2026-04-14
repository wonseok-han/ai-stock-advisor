'use client';

import { useQuery } from '@tanstack/react-query';

import { getNews } from '@/lib/api/news';

import type { NewsItem } from '@/types/news';

export function useNews(ticker: string, limit = 5) {
  return useQuery<NewsItem[]>({
    queryKey: ['news', ticker, limit],
    queryFn: () => getNews(ticker, limit),
    staleTime: 5 * 60 * 1000,
  });
}
