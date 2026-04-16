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
  updatedAt: string;
  disclaimer: string;
}

export interface MarketMover {
  ticker: string;
  name: string;
  price: number;
  change: number;
  changePercent: number;
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
