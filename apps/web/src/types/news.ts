/**
 * 뉴스 도메인 타입. BE NewsItem 과 1:1.
 * 참조: docs/02-design/features/phase2-rag-pipeline.design.md §3.1
 */
export type NewsSentiment = 'POSITIVE' | 'NEUTRAL' | 'NEGATIVE';

export interface NewsItem {
  ticker: string;
  articleUrlHash: string;
  source: string;
  sourceUrl: string;
  titleEn: string;
  titleKo: string | null;
  summaryKo: string | null;
  sentiment: NewsSentiment | null;
  publishedAt: string; // ISO 8601
  translatedAt: string | null;
  disclaimer: string;
}
