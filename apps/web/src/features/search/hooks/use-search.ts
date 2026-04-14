'use client';

import { useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';

import { searchStocks } from '@/lib/api/stocks';

import type { SearchHit } from '@/types/stock';

/**
 * Debounced 검색. 300ms (design §6.3).
 * 빈 query 는 enabled:false → 네트워크 호출 차단.
 */
export function useSearch(query: string, delayMs = 300) {
  const [debounced, setDebounced] = useState(query);

  useEffect(() => {
    const trimmed = query.trim();
    const t = setTimeout(() => setDebounced(trimmed), delayMs);
    return () => clearTimeout(t);
  }, [query, delayMs]);

  return useQuery<SearchHit[]>({
    queryKey: ['search', debounced],
    queryFn: ({ signal }) => searchStocks(debounced, signal),
    enabled: debounced.length > 0,
    staleTime: 60_000,
  });
}
