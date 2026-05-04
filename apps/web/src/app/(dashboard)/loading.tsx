export default function DashboardLoading() {
  return (
    <main className="mx-auto flex w-full max-w-7xl flex-1 flex-col gap-6 px-4 py-6 sm:px-6">
      {/* Market Overview */}
      <section className="card p-5">
        <div className="mb-4 h-5 w-28 animate-pulse rounded bg-bg-skeleton" />
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
        <div className="mb-4 h-5 w-32 animate-pulse rounded bg-bg-skeleton" />
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

      {/* Market Movers + News */}
      <div className="grid gap-6 lg:grid-cols-2">
        <section className="card p-5">
          <div className="mb-4 h-5 w-24 animate-pulse rounded bg-bg-skeleton" />
          <div className="space-y-3">
            {Array.from({ length: 5 }).map((_, i) => (
              <div key={i} className="h-12 animate-pulse rounded-lg bg-bg-skeleton" />
            ))}
          </div>
        </section>
        <section className="card p-5">
          <div className="mb-4 h-5 w-20 animate-pulse rounded bg-bg-skeleton" />
          <div className="space-y-3">
            {Array.from({ length: 4 }).map((_, i) => (
              <div key={i} className="h-16 animate-pulse rounded-lg bg-bg-skeleton" />
            ))}
          </div>
        </section>
      </div>
    </main>
  );
}
