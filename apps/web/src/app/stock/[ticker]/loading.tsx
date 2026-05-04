"use client";

import { InlineLoading } from "@/components/ui/panel-loading";

export default function StockDetailLoading() {
  return (
    <main className="mx-auto flex w-full max-w-7xl flex-1 flex-col gap-6 px-4 py-6 sm:px-6">
      {/* Header: ticker + price */}
      <section className="card p-5">
        <div className="mb-3">
          <InlineLoading text="종목 정보를 불러오고 있어요" />
        </div>
        <div className="flex items-center gap-4">
          <div className="h-10 w-10 animate-pulse rounded-full bg-bg-skeleton" />
          <div className="flex-1 space-y-2">
            <div className="h-6 w-32 animate-pulse rounded bg-bg-skeleton" />
            <div className="h-4 w-48 animate-pulse rounded bg-bg-skeleton" />
          </div>
          <div className="text-right space-y-1">
            <div className="h-7 w-24 animate-pulse rounded bg-bg-skeleton" />
            <div className="h-4 w-16 animate-pulse rounded bg-bg-skeleton" />
          </div>
        </div>
      </section>

      {/* Chart */}
      <section className="card p-5">
        <div className="mb-3">
          <InlineLoading text="차트 데이터를 불러오고 있어요" />
        </div>
        <div className="h-[300px] animate-pulse rounded-lg bg-bg-skeleton" />
      </section>

      {/* Indicators + Company Info */}
      <div className="grid gap-6 lg:grid-cols-2">
        <section className="card p-5">
          <div className="mb-4">
            <InlineLoading text="기술 지표를 계산하고 있어요" />
          </div>
          <div className="space-y-3">
            {Array.from({ length: 4 }).map((_, i) => (
              <div key={i} className="h-10 animate-pulse rounded-lg bg-bg-skeleton" />
            ))}
          </div>
        </section>
        <section className="card p-5">
          <div className="mb-4">
            <InlineLoading text="기업 정보를 확인하고 있어요" />
          </div>
          <div className="space-y-2">
            {Array.from({ length: 5 }).map((_, i) => (
              <div key={i} className="flex justify-between">
                <div className="h-4 w-20 animate-pulse rounded bg-bg-skeleton" />
                <div className="h-4 w-16 animate-pulse rounded bg-bg-skeleton" />
              </div>
            ))}
          </div>
        </section>
      </div>

      {/* News */}
      <section className="card p-5">
        <div className="mb-4">
          <InlineLoading text="관련 뉴스를 수집하고 있어요" />
        </div>
        <div className="space-y-3">
          {Array.from({ length: 3 }).map((_, i) => (
            <div key={i} className="h-16 animate-pulse rounded-lg bg-bg-skeleton" />
          ))}
        </div>
      </section>
    </main>
  );
}
