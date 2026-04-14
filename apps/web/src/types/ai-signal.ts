/**
 * AI 시그널 도메인 타입.
 * 참조: docs/02-design/features/phase2-rag-pipeline.design.md §3.1
 */
export type AiSignalClass =
  | 'STRONG_BUY'
  | 'BUY'
  | 'NEUTRAL'
  | 'SELL'
  | 'STRONG_SELL';

export type AiSignalTimeframe = 'SHORT' | 'MID' | 'LONG';

export interface AiSignal {
  ticker: string;
  signal: AiSignalClass;
  confidence: number; // 0.0 ~ 1.0
  timeframe: AiSignalTimeframe;
  rationale: string[];
  risks: string[];
  summaryKo: string;
  generatedAt: string; // ISO 8601
  modelName: string;
  disclaimer: string;
  fallback: boolean;
}
