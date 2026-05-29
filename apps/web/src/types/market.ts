export interface MarketIndex {
  symbol: string;
  name: string;
  price: number;
  change: number;
  changePercent: number;
  updatedAt: string;
}

export interface MarketOverview {
  indices: MarketIndex[];
  usdKrw: number | null;
  usdKrwChange: number | null;
  macro: MarketIndex[];
  updatedAt: string;
  priceLabel: string | null;
  disclaimer: string;
}

export interface SectorPerformance {
  sector: string;
  sectorKo: string;
  changePercent: number;
}

export interface MarketMover {
  ticker: string;
  name: string;
  price: number;
  change: number;
  changePercent: number;
  volume: number;
}

export interface MarketMovers {
  gainers: MarketMover[];
  losers: MarketMover[];
  poolSize: number;
  updatedAt: string;
  disclaimer: string;
}

export interface MarketNewsItem {
  id: number;
  source: string;
  sourceUrl: string;
  titleEn: string;
  titleKo: string | null;
  summaryKo: string | null;
  publishedAt: number;
  disclaimer: string;
}

export interface RegimeIndicator {
  key: string;
  name: string;
  value: number | null;
  unit: string | null;
  zone: string;
  note: string | null;
  prev1w: number | null;
  prev1m: number | null;
  prev1y: number | null;
}

export interface RegimeAxis {
  indicators: RegimeIndicator[];
}

export interface MarketRegimeComposite {
  score: number;
  label: string;
  labelKo: string;
}

export interface MarketRegime {
  asOf: string;
  composite: MarketRegimeComposite | null;
  axes: {
    valuation: RegimeAxis;
    riskSentiment: RegimeAxis;
    macro: RegimeAxis;
    trendBreadth: RegimeAxis;
  };
  disclaimer: string;
}

export interface MarketRegimeAi {
  asOf: string;
  aiSummary: string | null;
  disclaimer: string;
}
