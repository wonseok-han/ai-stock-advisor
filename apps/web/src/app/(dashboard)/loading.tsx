"use client";

import { InlineLoading } from "@/components/ui/panel-loading";

export default function DashboardLoading() {
  return (
    <main className="mx-auto flex w-full max-w-7xl flex-1 flex-col gap-6 px-4 py-6 sm:px-6">
      {/* Market Overview */}
      <section className="card p-5">
        <div className="mb-4">
          <InlineLoading text="시장 지수를 불러오고 있어요" />
        </div>
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 sm:grid-rows-[1fr_1fr]">
          <div className="col-span-2 row-span-2">
            <div className="h-full min-h-[160px] animate-pulse rounded-xl bg-bg-skeleton" />
          </div>
          <div className="h-20 animate-pulse rounded-xl bg-bg-skeleton" />
          <div className="h-20 animate-pulse rounded-xl bg-bg-skeleton" />
          <div className="h-20 animate-pulse rounded-xl bg-bg-skeleton" />
          <div className="h-20 animate-pulse rounded-xl bg-bg-skeleton" />
        </div>
      </section>

      {/* Sector Performance */}
      <section className="card p-5">
        <div className="mb-4">
          <InlineLoading text="섹터별 성과를 분석하고 있어요" />
        </div>
        <div className="space-y-3">
          {Array.from({ length: 6 }).map((_, i) => (
            <div key={i} className="flex items-center gap-3">
              <div className="h-4 w-24 animate-pulse rounded bg-bg-skeleton" />
              <div className="h-3 flex-1 animate-pulse rounded bg-bg-skeleton" />
              <div className="h-4 w-12 animate-pulse rounded bg-bg-skeleton" />
            </div>
          ))}
        </div>
      </section>

      {/* Market Regime */}
      <section className="card p-5">
        <div className="mb-4">
          <InlineLoading text="시장 국면 지표를 불러오고 있어요" />
        </div>
        {/* 종합 국면 바 */}
        <div className="h-16 animate-pulse rounded-xl bg-bg-skeleton" />
        {/* 공포·탐욕 게이지 */}
        <div className="mt-3 h-40 animate-pulse rounded-xl bg-bg-skeleton" />
        {/* 지표 그리드 */}
        <div className="mt-3 grid grid-cols-2 gap-3 sm:grid-cols-3">
          {Array.from({ length: 6 }).map((_, i) => (
            <div key={i} className="h-20 animate-pulse rounded-xl bg-bg-skeleton" />
          ))}
        </div>
        {/* 섹터·테마 히트맵 */}
        <div className="mt-4 h-28 animate-pulse rounded-xl bg-bg-skeleton" />
      </section>

      {/* Market News */}
      <section className="card p-5">
        <div className="mb-4">
          <InlineLoading text="최신 뉴스를 가져오고 있어요" />
        </div>
        <div className="space-y-3">
          {Array.from({ length: 4 }).map((_, i) => (
            <div key={i} className="h-16 animate-pulse rounded-lg bg-bg-skeleton" />
          ))}
        </div>
      </section>
    </main>
  );
}
