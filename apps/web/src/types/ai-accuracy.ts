/**
 * AI 시그널 정합도(Accuracy) 도메인 타입.
 *
 * 참조: docs/02-design/features/signal-accuracy.design.md §3.1, §4.2
 *
 * BE `com.nowini.ai.domain.AccuracySummary` 와 1:1 매핑.
 * `sampleSizeSufficient=false` 인 경우 `hitRate` 는 null, `bySignal` 은 빈 객체로 직렬화됨.
 */
export type AiAccuracySignal =
  | 'STRONG_BUY'
  | 'BUY'
  | 'NEUTRAL'
  | 'SELL'
  | 'STRONG_SELL';

export interface AiAccuracyBucketStat {
  count: number;
  hitRate: number; // 0.0 ~ 1.0
}

export interface AiAccuracySummary {
  window: number; // 7 | 30
  sampleSize: number;
  sampleSizeSufficient: boolean;
  hitRate: number | null; // sampleSizeSufficient=false 이면 null
  bySignal: Partial<Record<AiAccuracySignal, AiAccuracyBucketStat>>;
  evaluatedThrough: string | null; // ISO 8601
  message: string | null;
  disclaimer: string;
}

export type AiAccuracyWindow = 7 | 30;
