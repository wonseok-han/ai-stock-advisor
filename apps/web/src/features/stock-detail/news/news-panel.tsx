'use client';

import { PanelLoading } from '@/components/ui/panel-loading';
import { useNews } from '@/features/stock-detail/news/hooks/use-news';
import { cn } from '@/lib/cn';

import type { NewsItem, NewsSentiment } from '@/types/news';

export function NewsPanel({ ticker }: { ticker: string }) {
  const { data, isLoading, error } = useNews(ticker, 5);

  if (isLoading) {
    return <PanelLoading title="최근 뉴스" text="관련 뉴스를 가져오고 있어요" />;
  }
  if (error) {
    return (
      <PanelShell>
        <span className="text-danger">뉴스를 불러올 수 없습니다.</span>
      </PanelShell>
    );
  }
  if (!data || data.length === 0) {
    return <PanelShell>최근 관련 뉴스가 없습니다.</PanelShell>;
  }

  return (
    <section aria-label="최근 뉴스" className="card p-5">
      <h2 className="text-sm font-semibold text-fg">최근 뉴스</h2>
      <ul className="mt-4 flex flex-col divide-y divide-border">
        {data.map((item) => (
          <NewsRow key={item.articleUrlHash} item={item} />
        ))}
      </ul>
      {data[0]?.disclaimer ? (
        <p className="mt-4 text-[11px] text-fg-muted">{data[0].disclaimer}</p>
      ) : null}
    </section>
  );
}

function PanelShell({ children }: { children: React.ReactNode }) {
  return (
    <section className="card p-5 text-sm text-fg-muted">{children}</section>
  );
}

function NewsRow({ item }: { item: NewsItem }) {
  const title = item.titleKo ?? item.titleEn;
  return (
    <li className="flex flex-col gap-1 py-3 first:pt-0">
      <div className="flex items-start justify-between gap-3">
        <a
          href={item.sourceUrl}
          target="_blank"
          rel="noreferrer noopener"
          className="text-sm font-medium text-fg transition-colors hover:text-primary"
        >
          {title}
        </a>
        <SentimentBadge sentiment={item.sentiment} />
      </div>
      {item.summaryKo ? (
        <p className="text-xs text-fg-secondary">{item.summaryKo}</p>
      ) : null}
      <div className="flex items-center gap-2 text-[11px] text-fg-muted">
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
      <span className="rounded-md bg-bg-muted px-1.5 py-0.5 text-[10px] text-fg-muted">-</span>
    );
  }
  const map: Record<NewsSentiment, { label: string; cls: string }> = {
    POSITIVE: { label: '긍정', cls: 'bg-emerald-500/10 text-success' },
    NEUTRAL: { label: '중립', cls: 'bg-bg-muted text-fg-secondary' },
    NEGATIVE: { label: '부정', cls: 'bg-red-500/10 text-danger' },
  };
  const m = map[sentiment];
  return (
    <span className={cn('shrink-0 rounded-md px-1.5 py-0.5 text-[10px] font-medium', m.cls)}>
      {m.label}
    </span>
  );
}
