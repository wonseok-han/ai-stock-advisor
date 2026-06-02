"use client";

import { InlineLoading } from "@/components/ui/panel-loading";

export default function AiSignalLoading() {
  return (
    <main className="mx-auto flex w-full max-w-7xl flex-1 flex-col gap-6 px-4 py-6 sm:px-6">
      {/* 뒤로가기 링크 */}
      <div className="h-7 w-28 animate-pulse rounded-lg bg-bg-skeleton" />

      {/* StockHeader: ticker + price */}
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
          <div className="space-y-1 text-right">
            <div className="ml-auto h-7 w-24 animate-pulse rounded bg-bg-skeleton" />
            <div className="ml-auto h-4 w-16 animate-pulse rounded bg-bg-skeleton" />
          </div>
        </div>
      </section>

      {/* AiSignalPanel: AI 참고 분석 (단기·장기 2섹션) */}
      <section className="card p-5">
        <div className="mb-4">
          <InlineLoading text="AI가 시장 데이터와 뉴스를 종합 분석하고 있어요" />
        </div>
        <div className="space-y-4">
          <div className="h-32 animate-pulse rounded-xl bg-bg-skeleton" />
          <div className="h-32 animate-pulse rounded-xl bg-bg-skeleton" />
        </div>
      </section>
    </main>
  );
}
