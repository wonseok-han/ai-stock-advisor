export default function AiSignalLoading() {
  return (
    <main className="mx-auto flex w-full max-w-7xl flex-1 flex-col gap-6 px-4 py-6 sm:px-6">
      {/* Header */}
      <section className="card p-5">
        <div className="flex items-center gap-4">
          <div className="h-10 w-10 animate-pulse rounded-full bg-bg-skeleton" />
          <div className="flex-1 space-y-2">
            <div className="h-6 w-40 animate-pulse rounded bg-bg-skeleton" />
            <div className="h-4 w-56 animate-pulse rounded bg-bg-skeleton" />
          </div>
        </div>
      </section>

      {/* AI Signal Panel */}
      <section className="card p-5">
        <div className="mb-4 h-5 w-32 animate-pulse rounded bg-bg-skeleton" />
        <div className="space-y-4">
          <div className="h-24 animate-pulse rounded-lg bg-bg-skeleton" />
          <div className="h-24 animate-pulse rounded-lg bg-bg-skeleton" />
        </div>
      </section>

      {/* SEC Filings */}
      <section className="card p-5">
        <div className="mb-4 h-5 w-28 animate-pulse rounded bg-bg-skeleton" />
        <div className="space-y-3">
          {Array.from({ length: 3 }).map((_, i) => (
            <div key={i} className="h-20 animate-pulse rounded-lg bg-bg-skeleton" />
          ))}
        </div>
      </section>
    </main>
  );
}
