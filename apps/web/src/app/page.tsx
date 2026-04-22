import { Logo } from '@/components/brand/logo';
import { MarketDashboard } from '@/features/market-dashboard/market-dashboard';
import { SearchBox } from '@/features/search/search-box';

export default function Home() {
  return (
    <main className="mx-auto flex w-full max-w-5xl flex-1 flex-col gap-8 px-4 py-8 sm:px-6">
      <div className="flex flex-col items-center gap-3">
        <h1>
          <Logo size="lg" showTagline />
        </h1>
        <p className="text-base text-fg-secondary">
          미국 주식 참고/분석 도구. 투자 자문이 아닌 <strong>참고용 정보</strong>를
          제공합니다.
        </p>
      </div>
      <SearchBox />
      <MarketDashboard />
      <p className="text-center text-xs leading-relaxed text-fg-muted">
        본 서비스의 데이터와 분석은 참고용이며, 모든 투자 판단과 책임은 사용자
        본인에게 있습니다.
      </p>
    </main>
  );
}
