/**
 * AI 시그널 도메인 타입.
 * 참조:
 *  - docs/02-design/features/phase2-rag-pipeline.design.md §3.1 (v1)
 *  - docs/02-design/features/ai-analysis-deepening.design.md §3.1 / §4 (v2 확장)
 *
 * v2 확장 필드는 모두 optional — BE 가 null 로 내려보내면 렌더링 생략.
 */
export type AiSignalClass =
  | 'STRONG_BUY'
  | 'BUY'
  | 'NEUTRAL'
  | 'SELL'
  | 'STRONG_SELL';

export type AiSignalTimeframe = 'SHORT' | 'MID' | 'LONG';

export type ImpactDirection = 'POSITIVE' | 'NEGATIVE' | 'NEUTRAL';

export interface IndicatorInterpretation {
  indicator: string;
  value: string;
  meaningKo: string;
}

export interface NewsImpact {
  titleKo: string;
  impact: ImpactDirection;
  reasonKo: string;
  hoursAgo?: number | null;
}

export interface AiSignal {
  ticker: string;
  signal: AiSignalClass;
  confidence: number; // 0.0 ~ 1.0
  timeframe: AiSignalTimeframe;
  rationale: string[];
  risks: string[];
  summaryKo: string;
  // v2 — nullable / optional
  beginnerExplanation?: string | null;
  indicatorInterpretation?: IndicatorInterpretation[] | null;
  newsImpact?: NewsImpact[] | null;
  whatToWatch?: string[] | null;
  generatedAt: string; // ISO 8601
  modelName: string;
  disclaimer: string;
  fallback: boolean;
}
