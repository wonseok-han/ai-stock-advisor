import { dehydrate, HydrationBoundary, QueryClient } from '@tanstack/react-query';

import {
  getMarketMovers,
  getMarketNews,
  getMarketOverview,
  getSectorPerformance,
} from '@/lib/api/market';
import { DashboardView } from '@/features/market-dashboard/dashboard-view';

export default async function Home() {
  const queryClient = new QueryClient();

  await Promise.all([
    queryClient.prefetchQuery({
      queryKey: ['market', 'overview'],
      queryFn: () => getMarketOverview(),
    }),
    queryClient.prefetchQuery({
      queryKey: ['market', 'sectors'],
      queryFn: () => getSectorPerformance(),
    }),
    queryClient.prefetchQuery({
      queryKey: ['market', 'movers'],
      queryFn: () => getMarketMovers(),
    }),
    queryClient.prefetchQuery({
      queryKey: ['market', 'news'],
      queryFn: () => getMarketNews(),
    }),
  ]);

  return (
    <main className="mx-auto flex w-full max-w-7xl flex-1 flex-col gap-6 px-4 py-6 sm:px-6">
      <HydrationBoundary state={dehydrate(queryClient)}>
        <DashboardView />
      </HydrationBoundary>
    </main>
  );
}
