"use client";

import { cn } from "@/lib/cn";

import type {
  TimingFactor,
  TimingVerdict,
  TimingVerdictType,
} from "@/types/ai-signal";

export function TimingCard({ timing }: { timing: TimingVerdict }) {
  const { label, colorCls, bgCls, barCls } = verdictStyle(timing.verdict);

  return (
    <section aria-label="타이밍 판정" className="card brand-glow p-5">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <VerdictIcon verdict={timing.verdict} />
          <h2 className={cn("text-lg font-bold", colorCls)}>{label}</h2>
        </div>
        <span className="text-sm font-medium tabular-nums text-fg-secondary">
          조건 충족도 <span className="text-fg">{timing.score}%</span>
        </span>
      </div>

      <div className="mt-3 h-2 w-full overflow-hidden rounded-full bg-bg-muted">
        <div
          className={cn(
            "h-full rounded-full transition-all duration-500",
            barCls,
          )}
          style={{ width: `${timing.score}%` }}
        />
      </div>

      <div className="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-2">
        <FactorList
          title="충족된 조건"
          factors={timing.factorsMet}
          icon={<CheckIcon />}
        />
        <FactorList
          title="미충족 조건"
          factors={timing.factorsUnmet}
          icon={<XIcon />}
        />
      </div>

      <p className={cn("mt-4 rounded-xl p-3 text-sm text-fg-secondary", bgCls)}>
        {timing.summaryKo}
      </p>

      <p className="mt-3 text-xs text-fg-muted">{timing.disclaimerKo}</p>
    </section>
  );
}

function FactorList({
  title,
  factors,
  icon,
}: {
  title: string;
  factors: TimingFactor[];
  icon: React.ReactNode;
}) {
  if (factors.length === 0) return null;
  return (
    <div className="rounded-xl bg-bg-muted p-3">
      <h3 className="mb-2 text-xs font-semibold text-fg-muted">{title}</h3>
      <ul className="flex flex-col gap-1.5">
        {factors.map((f, i) => (
          <li
            key={i}
            className="flex items-start gap-2 text-xs text-fg-secondary"
          >
            <span className="mt-px shrink-0">{icon}</span>
            <span>
              <span className="font-medium text-fg">{f.factor}</span>
              {" — "}
              {f.detail}
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}

function CheckIcon() {
  return (
    <svg
      aria-hidden="true"
      className="h-4 w-4 text-success"
      fill="none"
      viewBox="0 0 24 24"
      strokeWidth={3}
      stroke="currentColor"
    >
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        d="M4.5 12.75l6 6 9-13.5"
      />
    </svg>
  );
}

function XIcon() {
  return (
    <svg
      aria-hidden="true"
      className="h-4 w-4 text-danger"
      fill="none"
      viewBox="0 0 24 24"
      strokeWidth={3}
      stroke="currentColor"
    >
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        d="M6 18L18 6M6 6l12 12"
      />
    </svg>
  );
}

function VerdictIcon({ verdict }: { verdict: TimingVerdictType }) {
  const base = "flex h-8 w-8 items-center justify-center rounded-xl";
  switch (verdict) {
    case "NOW":
      return (
        <span className={cn(base, "bg-emerald-500/15")}>
          <svg
            aria-hidden="true"
            className="h-5 w-5 text-emerald-500"
            fill="none"
            viewBox="0 0 24 24"
            strokeWidth={2}
            stroke="currentColor"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              d="M2 15L5 13L7 16L10 13L12 16L20 4m0 0h-4m4 0v4"
            />
          </svg>
        </span>
      );
    case "UNCERTAIN":
      return (
        <span className={cn(base, "bg-amber-500/15")}>
          <svg
            aria-hidden="true"
            className="h-5 w-5 text-amber-500"
            fill="none"
            viewBox="0 0 24 24"
            strokeWidth={2}
            stroke="currentColor"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              d="M3 12L6 9L9 14L12 10L15 13L18 8L21 12"
            />
          </svg>
        </span>
      );
    case "NOT_YET":
      return (
        <span className={cn(base, "bg-zinc-500/15")}>
          <svg
            aria-hidden="true"
            className="h-5 w-5 text-zinc-400"
            fill="none"
            viewBox="0 0 24 24"
            strokeWidth={2}
            stroke="currentColor"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              d="M3 8L7 14L11 13L15 15L19 14L21 16"
            />
          </svg>
        </span>
      );
  }
}

function verdictStyle(verdict: TimingVerdictType) {
  switch (verdict) {
    case "NOW":
      return {
        label: "지금이니?!",
        colorCls: "text-success",
        bgCls: "bg-emerald-500/5",
        barCls: "bg-emerald-500",
      };
    case "UNCERTAIN":
      return {
        label: "흠.. 애매한데?",
        colorCls: "text-amber-500 dark:text-amber-400",
        bgCls: "bg-amber-500/5",
        barCls: "bg-amber-400",
      };
    case "NOT_YET":
      return {
        label: "아직인듯?",
        colorCls: "text-fg-muted",
        bgCls: "bg-bg-muted",
        barCls: "bg-zinc-400 dark:bg-zinc-500",
      };
  }
}
