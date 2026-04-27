export type AiSignalClass =
  | 'STRONG_BUY'
  | 'BUY'
  | 'NEUTRAL'
  | 'SELL'
  | 'STRONG_SELL';

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

export interface SignalPerspective {
  signal: AiSignalClass;
  confidence: number;
  rationale: string[];
  risks: string[];
  summaryKo: string;
  beginnerExplanation?: string | null;
  indicatorInterpretation?: IndicatorInterpretation[] | null;
  newsImpact?: NewsImpact[] | null;
  whatToWatch?: string[] | null;
}

export interface AiSignal {
  ticker: string;
  shortTerm: SignalPerspective;
  longTerm: SignalPerspective;
  generatedAt: string;
  modelName: string;
  disclaimer: string;
  fallback: boolean;
}
