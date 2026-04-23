'use client';

import { useQuery } from '@tanstack/react-query';

import { getSectorPerformance } from '@/lib/api/market';

import type { SectorPerformance } from '@/types/market';

export function useSectorPerformance() {
  return useQuery<SectorPerformance[]>({
    queryKey: ['market', 'sectors'],
    queryFn: () => getSectorPerformance(),
    staleTime: 15 * 60_000,
    refetchInterval: 15 * 60_000,
    retry: 1,
  });
}
