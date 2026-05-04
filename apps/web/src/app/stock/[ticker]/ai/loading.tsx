"use client";

import { InlineLoading } from "@/components/ui/panel-loading";

export default function AiSignalLoading() {
  return (
    <main className="mx-auto flex w-full max-w-7xl flex-1 flex-col gap-6 px-4 py-6 sm:px-6">
      <section className="card p-5">
        <div className="mb-4">
          <InlineLoading text="AI가 시장 데이터를 종합 분석하고 있어요" />
        </div>
        <div className="space-y-4">
          <div className="h-24 animate-pulse rounded-lg bg-bg-skeleton" />
          <div className="h-24 animate-pulse rounded-lg bg-bg-skeleton" />
        </div>
      </section>
    </main>
  );
}
