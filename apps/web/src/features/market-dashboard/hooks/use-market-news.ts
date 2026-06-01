'use client';

import { useInfiniteQuery } from '@tanstack/react-query';

import { getMarketNews } from '@/lib/api/market';

const PAGE_SIZE = 10;

export function useMarketNews() {
  return useInfiniteQuery({
    queryKey: ['market', 'news'],
    queryFn: ({ pageParam }) => getMarketNews(PAGE_SIZE, pageParam),
    initialPageParam: undefined as number | undefined,
    getNextPageParam: (lastPage) =>
      lastPage.length < PAGE_SIZE
        ? undefined
        : lastPage[lastPage.length - 1]?.publishedAt,
    staleTime: 15 * 60_000,
    refetchInterval: 15 * 60_000,
    retry: 1,
  });
}
