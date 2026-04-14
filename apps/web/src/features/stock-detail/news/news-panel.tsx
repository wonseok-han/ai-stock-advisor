'use client';

import { useNews } from '@/features/stock-detail/news/hooks/use-news';
import { cn } from '@/lib/cn';

import type { NewsItem, NewsSentiment } from '@/types/news';

/**
 * 뉴스 패널 (design §4.1, §4.2).
 * - 최근 뉴스 헤드라인 한국어 번역 + 3줄 요약 + 감성 배지
 * - 원문 링크는 새 탭 open (noreferrer) — 외부 이동 명시
 * - 면책 문구 상시 노출
 */
export function NewsPanel({ ticker }: { ticker: string }) {
  const { data, isLoading, error } = useNews(ticker, 5);

  if (isLoading) {
    return <PanelShell>뉴스를 가져오는 중…</PanelShell>;
  }
  if (error) {
    return (
      <PanelShell>
        <span className="text-red-600">
          뉴스를 불러올 수 없습니다. 잠시 후 다시 시도해주세요.
        </span>
      </PanelShell>
    );
  }
  if (!data || data.length === 0) {
    return <PanelShell>최근 관련 뉴스가 없습니다.</PanelShell>;
  }

  const disclaimer = data[0]?.disclaimer;

  return (
    <section
      aria-label="최근 뉴스"
      className="rounded-lg border border-zinc-200 bg-white p-4 dark:border-zinc-800 dark:bg-zinc-900"
    >
      <h2 className="mb-3 text-sm font-semibold text-zinc-700 dark:text-zinc-300">
        최근 뉴스 (참고용)
      </h2>
      <ul className="flex flex-col divide-y divide-zinc-100 dark:divide-zinc-800">
        {data.map((item) => (
          <NewsRow key={item.articleUrlHash} item={item} />
        ))}
      </ul>
      {disclaimer ? (
        <p className="mt-3 text-xs text-zinc-500">{disclaimer}</p>
      ) : null}
    </section>
  );
}

function PanelShell({ children }: { children: React.ReactNode }) {
  return (
    <section className="rounded-lg border border-zinc-200 bg-white p-4 text-sm text-zinc-500 dark:border-zinc-800 dark:bg-zinc-900">
      {children}
    </section>
  );
}

function NewsRow({ item }: { item: NewsItem }) {
  const title = item.titleKo ?? item.titleEn;
  return (
    <li className="flex flex-col gap-1 py-3">
      <div className="flex items-start justify-between gap-3">
        <a
          href={item.sourceUrl}
          target="_blank"
          rel="noreferrer noopener"
          className="text-sm font-medium text-zinc-900 hover:underline dark:text-zinc-100"
        >
          {title}
        </a>
        <SentimentBadge sentiment={item.sentiment} />
      </div>
      {item.summaryKo ? (
        <p className="text-xs text-zinc-600 dark:text-zinc-400">{item.summaryKo}</p>
      ) : null}
      <div className="flex items-center gap-2 text-[11px] text-zinc-500">
        <span>{item.source}</span>
        <span aria-hidden="true">·</span>
        <time dateTime={item.publishedAt}>
          {new Date(item.publishedAt).toLocaleString('ko-KR')}
        </time>
      </div>
    </li>
  );
}

function SentimentBadge({ sentiment }: { sentiment: NewsSentiment | null }) {
  if (!sentiment) {
    return (
      <span className="rounded-sm bg-zinc-100 px-1.5 py-0.5 text-[10px] text-zinc-500 dark:bg-zinc-800">
        -
      </span>
    );
  }
  const map: Record<NewsSentiment, { label: string; cls: string }> = {
    POSITIVE: {
      label: '긍정',
      cls: 'bg-green-100 text-green-700 dark:bg-green-900/40 dark:text-green-300',
    },
    NEUTRAL: {
      label: '중립',
      cls: 'bg-zinc-100 text-zinc-600 dark:bg-zinc-800 dark:text-zinc-300',
    },
    NEGATIVE: {
      label: '부정',
      cls: 'bg-red-100 text-red-700 dark:bg-red-900/40 dark:text-red-300',
    },
  };
  const m = map[sentiment];
  return (
    <span
      className={cn(
        'shrink-0 rounded-sm px-1.5 py-0.5 text-[10px] font-medium',
        m.cls,
      )}
    >
      {m.label}
    </span>
  );
}
