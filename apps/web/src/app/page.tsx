import { MarketDashboard } from '@/features/market-dashboard/market-dashboard';

export default function Home() {
  return (
    <main className="mx-auto flex w-full max-w-7xl flex-1 flex-col gap-6 px-4 py-6 sm:px-6">
      <div className="flex items-baseline justify-between">
        <div>
          <h1 className="text-lg font-semibold text-fg">시장 현황</h1>
          <p className="mt-0.5 text-sm text-fg-muted">
            미국 주식 시장 요약 · 참고용 정보
          </p>
        </div>
      </div>
      <MarketDashboard />
    </main>
  );
}
