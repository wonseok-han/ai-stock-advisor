'use client';

import { CollapsibleSection } from '@/features/stock-detail/ai-signal/components/collapsible-section';
import { cn } from '@/lib/cn';

import type { ImpactDirection, NewsImpact as NewsImpactItem } from '@/types/ai-signal';

/**
 * AI 참고 분석 — 뉴스 영향 요약 리스트.
 * hoursAgo 기반으로 FreshnessBadge + generatedAt 역산한 발행 일시를 표기.
 * 참조: docs/02-design/features/ai-analysis-deepening.design.md §5
 */
export function NewsImpact({
  items,
  generatedAt,
}: {
  items?: NewsImpactItem[] | null;
  generatedAt?: string;
}) {
  if (!items || items.length === 0) return null;
  return (
    <CollapsibleSection title="최근 뉴스가 미친 영향">
      <ul className="flex flex-col gap-2">
        {items.map((it, idx) => (
          <li
            key={`${idx}-${it.titleKo}`}
            className="rounded-sm border border-zinc-100 bg-white px-2.5 py-2 text-xs dark:border-zinc-800 dark:bg-zinc-900"
          >
            <div className="flex items-start justify-between gap-2">
              <span className="font-semibold text-zinc-700 dark:text-zinc-200">
                {it.titleKo}
              </span>
              <div className="flex shrink-0 items-center gap-1">
                <FreshnessBadge hoursAgo={it.hoursAgo ?? null} />
                <ImpactBadge impact={it.impact} />
              </div>
            </div>
            <p className="mt-1 text-zinc-700 dark:text-zinc-300">{it.reasonKo}</p>
            <PublishedAt hoursAgo={it.hoursAgo ?? null} generatedAt={generatedAt} />
          </li>
        ))}
      </ul>
    </CollapsibleSection>
  );
}

function PublishedAt({
  hoursAgo,
  generatedAt,
}: {
  hoursAgo: number | null;
  generatedAt?: string;
}) {
  if (hoursAgo == null || !generatedAt) return null;
  const base = new Date(generatedAt);
  if (Number.isNaN(base.getTime())) return null;
  const published = new Date(base.getTime() - Math.max(0, hoursAgo) * 3600 * 1000);
  const label = formatPublished(published, hoursAgo);
  return (
    <time
      dateTime={published.toISOString()}
      className="mt-1.5 block text-[10px] text-zinc-400 dark:text-zinc-500"
    >
      {label}
    </time>
  );
}

function formatPublished(published: Date, hoursAgo: number): string {
  if (hoursAgo < 24) {
    // 24시간 내: "오늘 14:30"
    const time = published.toLocaleTimeString('ko-KR', {
      hour: '2-digit',
      minute: '2-digit',
      hour12: false,
    });
    return `오늘 ${time}`;
  }
  if (hoursAgo < 24 * 7) {
    // 일주일 내: "4월 19일 (월)"
    return published.toLocaleDateString('ko-KR', {
      month: 'long',
      day: 'numeric',
      weekday: 'short',
    });
  }
  // 그 이상: "2026년 4월 15일"
  return published.toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  });
}

function FreshnessBadge({ hoursAgo }: { hoursAgo: number | null }) {
  if (hoursAgo == null) return null;
  const { label, cls } = freshnessOf(hoursAgo);
  return (
    <span
      className={cn(
        'rounded-sm px-1.5 py-0.5 text-[10px] font-medium',
        cls,
      )}
      aria-label={`뉴스 신선도: ${label}`}
    >
      {label}
    </span>
  );
}

function freshnessOf(hoursAgo: number): { label: string; cls: string } {
  if (hoursAgo < 24) {
    return {
      label: `${hoursAgo}시간 전`,
      cls: 'bg-blue-100 text-blue-800 dark:bg-blue-900/40 dark:text-blue-200',
    };
  }
  if (hoursAgo < 72) {
    return {
      label: `${Math.round(hoursAgo / 24)}일 전`,
      cls: 'bg-zinc-100 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-200',
    };
  }
  return {
    label: '오래된 뉴스',
    cls: 'bg-amber-50 text-amber-800 dark:bg-amber-950/40 dark:text-amber-200',
  };
}

function ImpactBadge({ impact }: { impact: ImpactDirection }) {
  const { label, cls } = impactStyle(impact);
  return (
    <span
      className={cn('rounded-sm px-1.5 py-0.5 text-[10px] font-semibold', cls)}
      aria-label={`영향: ${label}`}
    >
      {label}
    </span>
  );
}

function impactStyle(impact: ImpactDirection): { label: string; cls: string } {
  switch (impact) {
    case 'POSITIVE':
      return {
        label: '긍정',
        cls: 'bg-green-100 text-green-800 dark:bg-green-900/40 dark:text-green-200',
      };
    case 'NEGATIVE':
      return {
        label: '부정',
        cls: 'bg-red-100 text-red-800 dark:bg-red-900/40 dark:text-red-200',
      };
    case 'NEUTRAL':
      return {
        label: '중립',
        cls: 'bg-zinc-100 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-200',
      };
  }
}
