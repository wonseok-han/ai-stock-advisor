# mvp Design Document

> **Summary**: 초보 한국어 라이트 투자자용 미국 주식 통합 분석 대시보드 MVP의 아키텍처·데이터 모델·API 계약·UI 구조·RAG 파이프라인·법적 가드 레이어 설계.
>
> **Project**: AI Stock Advisor
> **Version**: 0.1 (Phase 1 착수 전)
> **Author**: wonseok-han
> **Date**: 2026-04-13
> **Status**: Draft
> **Planning Doc**: [mvp.plan.md](../../01-plan/features/mvp.plan.md)
> **PRD**: [mvp.prd.md](../../00-pm/mvp.prd.md)

### Pipeline References

| Phase | Document | Status |
|-------|----------|--------|
| Phase 1 | [Schema Definition](../../01-plan/schema.md) | ❌ (본 문서에 인라인) |
| Phase 2 | [Coding Conventions](../../01-plan/conventions.md) | ❌ (CLAUDE.md 에 인라인) |
| Phase 3 | Mockup | ❌ (본 문서 §5 에 ASCII 와이어) |
| Phase 4 | API Spec | ❌ (본 문서 §4 에 인라인) |

---

## 1. Overview

### 1.1 Design Goals

1. **서버 측 RAG 파이프라인 고정** — LLM 입력은 반드시 서버가 조립한 구조화 컨텍스트(지표 JSON + 뉴스 요약 + 시장 스냅샷). 사용자 입력 직접 삽입 금지 → 할루시네이션·프롬프트 인젝션 방어.
2. **4-level 금지 용어 가드** — (코드 상수) + (프롬프트 시스템 메시지) + (LLM 응답 validator) + (정적 카피 grep CI) 모두에서 금지 용어 검출 시 실패/대체. 법적 R1 리스크 상시 차단.
3. **캐시 키 설계로 비용 상한** — 종목 단위 캐시 키 + TTL 계층화 (시세 30s / 지표 5m / AI 시그널 30m / 뉴스 10m / 프로파일 24h). AI 호출 **MAU 1K 기준 월 ≤ $5** 상한.
4. **Clean separation (FE/BE)** — FE 는 BE 의 `/api/v1/*` 만 호출. 외부 API(Finnhub/Gemini) 직접 호출 금지. 키 노출 방지 + 캐시·금지어 가드 단일 지점화.
5. **모바일 퍼스트** — 375px → 768px → 1200px+ 3단 반응형. 종목 상세 단일 스크롤 앱 유사 UX.
6. **SEO 10종목 SSR** — 인기 10종목 페이지는 Next.js `generateStaticParams` + ISR (재검증 1h).

### 1.2 Design Principles

- **외부 의존성은 어댑터 패턴** — Finnhub/AlphaVantage/Gemini 각 클라이언트를 인터페이스 + 구현체로 분리. 2차 fallback 교체 용이.
- **"판단"을 LLM 에 위임하지 않음** — LLM 은 "지표 요약 + 리스크 나열"만. 최종 의사결정 언어는 사용자 몫. `signal` 은 `bullish/neutral/bearish` 3-상태 + `confidence` 는 `low/mid/high` 라벨만.
- **DB 는 MVP 에 최소** — 시세·지표·뉴스는 전부 외부 + Redis. PostgreSQL 은 MVP 에서 `popular_tickers`, `ai_signal_history`, `legal_disclaimer_audit` 3개 테이블만. 유저·북마크는 Phase 4 확장 지점.
- **타임아웃 우선 설계** — 외부 호출 기본 3s 타임아웃, LLM 8s. 실패 시 부분 응답(`partial: true` 필드) 반환.
- **로그 이벤트 표준화** — `{event, ticker, latencyMs, cacheHit, userId?, requestId}` JSON 구조 단일 포맷.

---

## 2. Architecture

### 2.1 Component Diagram

```
┌────────────────────────────────────────────────────────────────────────┐
│                           CLIENT (Browser)                             │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ Next.js 14 App Router (apps/web)                                 │  │
│  │  src/app/                       src/features/                    │  │
│  │   ├─ /                          ├─ market-dashboard/             │  │
│  │   ├─ /stock/[ticker]            ├─ stock-detail/                 │  │
│  │   ├─ /market                    │   ├─ chart/ (TradingView)      │  │
│  │   ├─ /glossary (F9)             │   ├─ indicators/               │  │
│  │   └─ /legal/* (terms, privacy)  │   ├─ ai-signal/                │  │
│  │                                 │   └─ news/                     │  │
│  │  React Query (server state) ·  Zustand (ui state) · Tailwind   │  │
│  └───────────────────────┬──────────────────────────────────────────┘  │
└──────────────────────────┼─────────────────────────────────────────────┘
                           │ HTTPS · /api/v1/*
                           ▼
┌────────────────────────────────────────────────────────────────────────┐
│                      BACKEND (Spring Boot 3)                           │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ Controllers (얇음) — @RestController                              │  │
│  │   stock/ · market/ · news/ · ai/ · legal/                        │  │
│  └───────────────────┬──────────────────────────────────────────────┘  │
│  ┌───────────────────┴──────────────────────────────────────────────┐  │
│  │ Services (도메인 로직)                                            │  │
│  │   ┌──────────┐  ┌──────────┐  ┌──────────────────────────────┐  │  │
│  │   │ Search   │  │ Quote    │  │ AI Signal Pipeline (RAG)     │  │  │
│  │   │ Service  │  │ Service  │  │  1. ContextAssembler         │  │  │
│  │   │          │  │          │  │  2. PromptBuilder (jinja)    │  │  │
│  │   │          │  │          │  │  3. GeminiClient             │  │  │
│  │   │          │  │          │  │  4. ResponseValidator        │  │  │
│  │   │          │  │          │  │     (schema + forbidden)     │  │  │
│  │   └──────────┘  └──────────┘  └──────────────────────────────┘  │  │
│  │   ┌──────────────┐  ┌──────────────┐  ┌────────────────────┐    │  │
│  │   │ Indicator    │  │ News         │  │ Market Dashboard   │    │  │
│  │   │ Service(ta4j)│  │ Service (+LLM│  │ Service            │    │  │
│  │   │              │  │ 요약)        │  │                    │    │  │
│  │   └──────────────┘  └──────────────┘  └────────────────────┘    │  │
│  └───────────────────┬──────────────────────────────────────────────┘  │
│  ┌───────────────────┴──────────────────────────────────────────────┐  │
│  │ Infra (Adapters / Clients)                                       │  │
│  │   FinnhubClient · AlphaVantageClient · GeminiClient              │  │
│  │   RedisCacheAdapter · JpaRepositories · LegalGuard               │  │
│  └──────────────────────────────────────────────────────────────────┘  │
└────────┬─────────────────┬────────────────┬───────────────┬────────────┘
         │                 │                │               │
         ▼                 ▼                ▼               ▼
  ┌─────────────┐  ┌──────────────┐  ┌───────────┐  ┌──────────────────┐
  │ Upstash     │  │ Supabase     │  │ Finnhub   │  │ Google Gemini    │
  │ Redis       │  │ PostgreSQL   │  │ (+ Alpha  │  │ 1.5 Flash        │
  │ (캐시)      │  │ (메타/이력)  │  │  Vantage) │  │ (판단)           │
  └─────────────┘  └──────────────┘  └───────────┘  └──────────────────┘
```

### 2.2 Data Flow — 종목 상세 요청 (Happy Path)

```
[User] /stock/AAPL 진입
  └─▶ Next.js: SSR 또는 CSR fetch → BE /api/v1/stocks/AAPL/detail
        └─▶ StockDetailService.getDetail(ticker)
              ├─▶ QuoteService.getQuote ───▶ Redis("quote:AAPL")
              │     └─▶ (miss) FinnhubClient.quote ─▶ Redis set (TTL 30s)
              ├─▶ IndicatorService.compute ──▶ Redis("ind:AAPL")
              │     └─▶ (miss) FinnhubClient.candles ─▶ ta4j 계산 ─▶ set (TTL 5m)
              ├─▶ NewsService.getKoNews ────▶ Redis("news:AAPL")
              │     └─▶ (miss) FinnhubClient.companyNews
              │            ─▶ GeminiClient.summarizeKo (배치)
              │            ─▶ set (TTL 10m)
              ├─▶ AiSignalService.getSignal ▶ Redis("ai:AAPL:v1")
              │     └─▶ (miss) ContextAssembler.build
              │            ─▶ PromptBuilder (시스템 + context + 출력 스키마)
              │            ─▶ GeminiClient.generateJson
              │            ─▶ ResponseValidator (schema + forbidden + safety)
              │            ─▶ set (TTL 30m) + ai_signal_history INSERT
              └─▶ compose DTO → return JSON

[LegalGuard Filter] 응답 직전 전수 금지어 스캔 (defense-in-depth)
```

### 2.3 Dependencies

| Component | Depends On | Purpose |
|-----------|-----------|---------|
| `StockDetailService` | QuoteService, IndicatorService, NewsService, AiSignalService | 단일 엔드포인트에서 4 블록 조립 |
| `AiSignalService` | IndicatorService, NewsService, MarketDashboardService, GeminiClient | RAG 컨텍스트 조립 |
| `IndicatorService` | FinnhubClient(candles) | ta4j 계산 입력 |
| `NewsService` | FinnhubClient(news), GeminiClient(요약) | 한국어 요약 |
| 모든 외부 호출 서비스 | `RedisCacheAdapter`, `Resilience4j` | 캐시 + 서킷브레이커/타임아웃 |
| 모든 응답 | `LegalGuard` | 금지어 최종 스캔 |
| FE 전체 | `@/lib/api` (React Query wrapper) | BE 만 호출 (외부 직접 호출 금지) |

---

## 3. Data Model

### 3.1 Domain Entities (Types, TypeScript)

FE 는 BE DTO 를 그대로 상속. 아래는 핵심 타입만:

```typescript
// src/types/stock.ts
export type Signal = 'bullish' | 'neutral' | 'bearish';
export type Confidence = 'low' | 'mid' | 'high';
export type TimeFrame = '1D' | '1W' | '1M' | '3M' | '1Y' | '5Y';

export interface StockProfile {
  ticker: string;
  name: string;
  exchange: string;          // "NASDAQ"
  currency: string;          // "USD"
  logoUrl?: string;
  industry?: string;
  marketCap?: number;
}

export interface Quote {
  ticker: string;
  price: number;
  change: number;
  changePercent: number;
  high: number;
  low: number;
  open: number;
  previousClose: number;
  volume: number;
  updatedAt: string;         // ISO 8601
}

export interface Candle {
  time: number;              // unix seconds
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
}

export interface IndicatorSnapshot {
  ticker: string;
  rsi14: number;             // 0~100
  macd: { macd: number; signal: number; histogram: number };
  bollinger: { upper: number; middle: number; lower: number; percentB: number };
  ma: { ma5: number; ma20: number; ma60: number };
  tooltipsKo: Record<string, string>;
}

export interface NewsItem {
  id: string;
  source: string;
  url: string;                // 원문 링크 (필수)
  publishedAt: string;
  titleKo: string;            // LLM 번역
  summaryKo: string;          // 3줄 요약
  tickers: string[];
}

export interface AiSignal {
  ticker: string;
  signal: Signal;
  confidence: Confidence;
  summaryKo: string;          // 한국어 3~5문장
  rationale: string[];        // 근거 3~5개 (bullet)
  risks: string[];            // 리스크 3~5개 (bullet)
  generatedAt: string;
  modelVersion: string;       // "gemini-1.5-flash-001"
  contextHash: string;        // 입력 해시 (재현성/캐시키)
}

export interface MarketSnapshot {
  indices: { spx: Quote; ndx: Quote; dji: Quote; vix: Quote };
  fx: { usdKrw: number };
  rates: { ust10y: number };
  topMovers: { gainers: Quote[]; losers: Quote[]; mostActive: Quote[] };
  newsHeadlines: NewsItem[];
  updatedAt: string;
}
```

### 3.2 Entity Relationships

```
StockProfile ──── 1 ─── * ──▶ Quote  (시점별, 캐시만)
     │
     ├─── 1 ─── * ──▶ Candle
     ├─── 1 ─── * ──▶ IndicatorSnapshot
     ├─── 1 ─── * ──▶ NewsItem
     └─── 1 ─── * ──▶ AiSignal (ai_signal_history 테이블)

MarketSnapshot (전체 1개, 캐시만, TTL 60s)

PopularTicker ──── (인기 10종목 SEO 랜딩용 고정 목록)

LegalDisclaimerAudit ──── (면책 UI 변경 이력 추적)

Phase 4:
User ──── 1 ─── * ──▶ Bookmark ─── N ─── 1 ──▶ StockProfile
User ──── 1 ─── * ──▶ Notification
```

### 3.3 Database Schema (PostgreSQL · Flyway V1__init.sql)

MVP 는 **외부 API + Redis 캐시 중심**이므로 DB 스키마는 최소:

```sql
-- V1__init.sql

-- 인기 종목 마스터 (SEO SSR 랜딩용)
CREATE TABLE popular_tickers (
  ticker          VARCHAR(10) PRIMARY KEY,
  name            VARCHAR(200) NOT NULL,
  display_order   SMALLINT NOT NULL,
  is_active       BOOLEAN NOT NULL DEFAULT TRUE,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- AI 시그널 이력 (감사·재현성·품질 분석용, PII 없음)
CREATE TABLE ai_signal_history (
  id              BIGSERIAL PRIMARY KEY,
  ticker          VARCHAR(10) NOT NULL,
  signal          VARCHAR(10) NOT NULL CHECK (signal IN ('bullish','neutral','bearish')),
  confidence      VARCHAR(10) NOT NULL CHECK (confidence IN ('low','mid','high')),
  summary_ko      TEXT NOT NULL,
  rationale       JSONB NOT NULL,
  risks           JSONB NOT NULL,
  context_hash    CHAR(64) NOT NULL,
  model_version   VARCHAR(50) NOT NULL,
  prompt_version  VARCHAR(20) NOT NULL,
  latency_ms      INTEGER NOT NULL,
  cache_hit       BOOLEAN NOT NULL DEFAULT FALSE,
  generated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_ai_signal_history_ticker_time ON ai_signal_history (ticker, generated_at DESC);
CREATE INDEX idx_ai_signal_history_hash ON ai_signal_history (context_hash);

-- 면책 고지 감사 (버전 변경 이력)
CREATE TABLE legal_disclaimer_audit (
  id              BIGSERIAL PRIMARY KEY,
  page            VARCHAR(100) NOT NULL,     -- "/", "/stock/[ticker]", "footer"
  version         VARCHAR(20) NOT NULL,
  content_hash    CHAR(64) NOT NULL,
  changed_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_legal_disclaimer_audit_page ON legal_disclaimer_audit (page, changed_at DESC);
```

### 3.4 Redis 캐시 키 설계

| Key Pattern | Value | TTL | Notes |
|-------------|-------|:---:|-------|
| `profile:{ticker}` | StockProfile JSON | 24h | 거의 변하지 않음 |
| `quote:{ticker}` | Quote JSON | 30s | 시세, 짧은 TTL |
| `candle:{ticker}:{tf}` | Candle[] JSON | 5m (1D) / 1h (1W+) | 타임프레임별 분리 |
| `ind:{ticker}` | IndicatorSnapshot JSON | 5m | ta4j 계산 결과 |
| `news:co:{ticker}` | NewsItem[] JSON | 10m | 한국어 요약 포함 |
| `news:market` | NewsItem[] JSON | 10m | 시장 뉴스 공용 |
| `ai:{ticker}:v{promptVer}` | AiSignal JSON | 30m | **비용 통제 핵심** |
| `market:snapshot` | MarketSnapshot JSON | 60s | 대시보드 통합 |
| `search:{query}` | StockProfile[] (top 10) | 1h | 자동완성 |
| `popular:tickers` | string[] (ordered) | 1h | 랜딩 |
| `forbidden:version` | string (CI 주입) | - | 금지어 리스트 버전 |

---

## 4. API Specification

### 4.1 Endpoint List (BE: `/api/v1/*`)

| Method | Path | Description | Auth | Phase |
|--------|------|-------------|:----:|:---:|
| GET | `/api/v1/search?q={query}` | 티커/회사명 검색 자동완성 (top 10) | — | 1 |
| GET | `/api/v1/stocks/{ticker}/profile` | 종목 프로파일 | — | 1 |
| GET | `/api/v1/stocks/{ticker}/quote` | 현재 시세 | — | 1 |
| GET | `/api/v1/stocks/{ticker}/candles?tf={1D\|1W\|1M\|3M\|1Y\|5Y}` | 캔들 | — | 1 |
| GET | `/api/v1/stocks/{ticker}/indicators` | RSI/MACD/Bollinger/MA + 툴팁 | — | 1 |
| GET | `/api/v1/stocks/{ticker}/news?limit=10` | 종목 뉴스 (한국어 요약) | — | 2 |
| GET | `/api/v1/stocks/{ticker}/ai-signal` | AI 시그널 (RAG) | — | 2 |
| **GET** | **`/api/v1/stocks/{ticker}/detail`** | **상단 4개 블록 통합 (FE 단일 호출)** | — | 2 |
| GET | `/api/v1/market/snapshot` | 시장 대시보드 통합 | — | 3 |
| GET | `/api/v1/popular-tickers` | SEO 랜딩용 인기 10종목 | — | 1 |
| GET | `/api/v1/health` | 헬스체크 + 의존성 상태 | — | 1 |
| GET | `/api/v1/legal/disclaimer?page={path}` | 면책 고지 버전/내용 | — | 3 |
| POST | `/api/v1/auth/verify` | JWT 검증 (Phase 4) | Bearer | 4 |
| GET | `/api/v1/bookmarks` | 북마크 조회 (Phase 4) | Bearer | 4 |

### 4.2 Detailed Specification

#### `GET /api/v1/stocks/{ticker}/detail`

FE 종목 상세 페이지 단일 호출 엔드포인트. 내부에서 4개 서비스 병렬 호출 (Java 21 virtual threads).

**Query Params:**
- `timeframe` (optional, default `1D`): `1D|1W|1M|3M|1Y|5Y`

**Response (200 OK):**
```json
{
  "profile": {
    "ticker": "AAPL",
    "name": "Apple Inc.",
    "exchange": "NASDAQ",
    "currency": "USD",
    "logoUrl": "https://...",
    "industry": "Technology",
    "marketCap": 3150000000000
  },
  "quote": {
    "ticker": "AAPL",
    "price": 178.23,
    "change": 1.12,
    "changePercent": 0.63,
    "high": 179.45,
    "low": 176.88,
    "open": 177.10,
    "previousClose": 177.11,
    "volume": 52341234,
    "updatedAt": "2026-04-13T20:45:00Z"
  },
  "candles": [
    { "time": 1713023400, "open": 177.1, "high": 179.45, "low": 176.88, "close": 178.23, "volume": 52341234 }
  ],
  "indicators": {
    "ticker": "AAPL",
    "rsi14": 58.2,
    "macd": { "macd": 1.23, "signal": 0.98, "histogram": 0.25 },
    "bollinger": { "upper": 182.1, "middle": 176.3, "lower": 170.5, "percentB": 0.67 },
    "ma": { "ma5": 177.8, "ma20": 176.3, "ma60": 172.1 },
    "tooltipsKo": {
      "rsi14": "RSI 14일. 70 이상 과매수, 30 이하 과매도 경향(확정 신호 아님).",
      "macd": "단기(12)-장기(26) 이동평균 차이. 신호선 교차를 전환 참고로 봄.",
      "bollinger": "20일 이동평균 ± 2표준편차. 상단 터치 시 단기 과열 경향.",
      "ma": "5/20/60일 이동평균. 배열로 추세 방향 참고."
    }
  },
  "news": [
    {
      "id": "fh_abc123",
      "source": "Reuters",
      "url": "https://www.reuters.com/...",
      "publishedAt": "2026-04-13T14:30:00Z",
      "titleKo": "애플, 1분기 아이폰 판매 예상치 상회 전망",
      "summaryKo": "애널리스트들은 ... (3줄 요약)",
      "tickers": ["AAPL"]
    }
  ],
  "aiSignal": {
    "ticker": "AAPL",
    "signal": "neutral",
    "confidence": "mid",
    "summaryKo": "RSI 중립 구간이고 MACD 히스토그램 양전환 초기입니다. 다만 실적 발표를 앞두고 ...",
    "rationale": [
      "MACD 히스토그램이 양전환 초기 (0.25)",
      "20일/60일 이평선 골든크로스 형성",
      "거래량 20일 평균 대비 +12%"
    ],
    "risks": [
      "실적 발표 D-3 — 가이던스 서프라이즈 가능성",
      "RSI 58 — 과매수 임계 접근",
      "전반적 시장 VIX 상승 국면"
    ],
    "generatedAt": "2026-04-13T20:15:00Z",
    "modelVersion": "gemini-1.5-flash-001",
    "contextHash": "a1b2c3..."
  },
  "disclaimer": {
    "page": "/stock/AAPL",
    "version": "v1.0",
    "text": "본 서비스는 투자 자문이 아닌 분석 도구입니다. 모든 투자 판단과 책임은 사용자 본인에게 있습니다."
  },
  "partial": false,
  "meta": {
    "requestId": "req_abc123",
    "timestamp": "2026-04-13T20:45:01.234Z"
  }
}
```

**Partial Response** (일부 블록 실패 시):
```json
{
  "profile": { ... },
  "quote": { ... },
  "candles": [ ... ],
  "indicators": null,
  "news": [],
  "aiSignal": null,
  "partial": true,
  "errors": [
    { "block": "aiSignal", "code": "LLM_TIMEOUT", "message": "AI 해석을 준비 중입니다." },
    { "block": "indicators", "code": "UPSTREAM_RATE_LIMIT", "message": "지표 계산이 일시적으로 지연됩니다." }
  ]
}
```

**Error Responses:**
- `400 Bad Request`: ticker 포맷 불량 (정규식 `^[A-Z]{1,5}(\.[A-Z])?$`)
- `404 Not Found`: 외부 API 에서 ticker 검색 결과 없음
- `429 Too Many Requests`: Finnhub/Gemini rate limit 도달
- `500 Internal Server Error`: 예기치 못한 예외
- `503 Service Unavailable`: 전체 외부 의존성 장애

#### `GET /api/v1/search?q={query}`

**Response (200 OK):**
```json
{
  "results": [
    { "ticker": "AAPL", "name": "Apple Inc.", "exchange": "NASDAQ", "matchType": "ticker" },
    { "ticker": "AAPU", "name": "Direxion Apple Bull 1.5X", "exchange": "NYSE Arca", "matchType": "ticker" }
  ],
  "query": "aap",
  "cachedAt": "2026-04-13T20:30:00Z"
}
```

#### `GET /api/v1/market/snapshot`

`MarketSnapshot` 타입 그대로. TTL 60s.

#### `GET /api/v1/legal/disclaimer?page={path}`

**Response:**
```json
{
  "page": "/stock/[ticker]",
  "version": "v1.0",
  "text": "본 서비스는 투자 자문이 아닌 분석 도구입니다. ...",
  "href": "/legal/disclaimer",
  "severity": "info"
}
```

### 4.3 External API Contracts (BE ↔ 외부)

| Provider | Endpoint | Purpose | Rate Limit | Fallback |
|----------|----------|---------|:---:|----------|
| Finnhub | `/search?q=` | 검색 | 60 req/min (free) | 캐시 후 Redis search 결과 |
| Finnhub | `/stock/profile2?symbol=` | 프로파일 | — | AlphaVantage `OVERVIEW` |
| Finnhub | `/quote?symbol=` | 시세 | — | AlphaVantage `GLOBAL_QUOTE` |
| Finnhub | `/stock/candle?symbol=&resolution=D&from=&to=` | 캔들 | — | AlphaVantage `TIME_SERIES_DAILY` |
| Finnhub | `/company-news?symbol=&from=&to=` | 종목 뉴스 | — | 없음 (뉴스 빈 배열 허용) |
| Finnhub | `/news?category=general` | 시장 뉴스 | — | 없음 |
| Google Gemini | `generateContent` (gemini-1.5-flash) | 번역·요약·AI 시그널 | RPM/TPM 쿼터 | **fallback 없음 — partial 응답** |

### 4.4 RAG Prompt Contract (AI Signal)

**System Prompt (고정 버전 `v1.0`):**
```
당신은 "분석 도구" 역할을 하는 한국어 자연어 요약 엔진입니다.

규칙:
1. 절대 "매수" "매도" "투자 추천" "수익 보장" 같은 표현을 사용하지 마십시오.
2. "~로 보입니다" "~일 수 있습니다" 등 추정형 어조만 사용하십시오.
3. 근거(rationale)는 반드시 제공된 지표·뉴스·시장 데이터에서만 인용하세요.
4. 리스크(risks)를 rationale 과 동일한 개수 이상 명시하세요.
5. 출력은 지정된 JSON 스키마만 반환 (설명 문장 금지).
```

**User Prompt Template:**
```
[종목] {ticker} ({name}, {exchange})
[시세] price={price}, change%={changePercent}, volume={volume}
[지표]
  RSI(14) = {rsi14}
  MACD = {macd}, signal = {macdSignal}, histogram = {macdHistogram}
  Bollinger: upper={upper}, middle={middle}, lower={lower}, %B={percentB}
  MA: MA5={ma5}, MA20={ma20}, MA60={ma60}
[최근 뉴스 (한국어 요약)]
  1. {titleKo1} — {summaryKo1}
  2. {titleKo2} — {summaryKo2}
  3. {titleKo3} — {summaryKo3}
[시장 맥락] SPX={spxChange%}, VIX={vix}, USD/KRW={usdKrw}

위 데이터만 근거로 아래 스키마에 맞춰 JSON 만 반환하세요.
```

**Output JSON Schema (strict):**
```json
{
  "signal": "bullish | neutral | bearish",
  "confidence": "low | mid | high",
  "summaryKo": "string (3~5 문장, 80~300자)",
  "rationale": ["string", "string", "..."]  (3~5 bullets),
  "risks": ["string", "string", "..."]       (3~5 bullets)
}
```

**Validator** (서버 측):
1. JSON 스키마 준수 (Jackson `@JsonTypeInfo` + Bean Validation)
2. 금지어 grep (`매수|매도|추천|보장|확실|반드시`) → 탐지 시 1회 재시도 (temperature 낮춤), 2회 실패 시 neutral fallback
3. `rationale.length >= 3`, `risks.length >= rationale.length` 강제
4. 응답 `signal/confidence` enum 검증

---

## 5. UI/UX Design

### 5.1 Screen Layout — 종목 상세 (모바일 우선)

```
┌─ MOBILE (375px) ───────────────┐
│ [≡] AI Stock Advisor           │ ← Header (검색창 토글)
├────────────────────────────────┤
│ ⚠️ 투자 자문 아님, 참고용      │ ← DisclaimerBanner (고정)
├────────────────────────────────┤
│ AAPL · Apple Inc. · NASDAQ     │ ← StockHeader (ticker + name)
│ $178.23  +1.12  (+0.63%)   📈 │
├────────────────────────────────┤
│ [1D][1W][1M][3M][1Y][5Y]      │ ← TimeFrameTabs
│ ┌────────────────────────────┐ │
│ │  TradingView Lightweight   │ │ ← ChartPanel (canvas)
│ │  Candle + MA + BB + 거래량  │ │
│ └────────────────────────────┘ │
├────────────────────────────────┤
│ 📊 기술 지표                    │ ← IndicatorsPanel
│ ┌──────────┬──────────────────┐│
│ │ RSI 58.2 │ 🟡 중립          ││  (ℹ 툴팁)
│ ├──────────┼──────────────────┤│
│ │ MACD     │ 양전환 초기       ││
│ ├──────────┼──────────────────┤│
│ │ Bollinger│ %B 0.67 (중상)   ││
│ └──────────┴──────────────────┘│
├────────────────────────────────┤
│ 🤖 AI 시그널 (참고용)          │ ← AiSignalPanel
│ [중립 · 신뢰도 중간]            │   - Badge
│ RSI 중립 구간이고 MACD          │   - summaryKo
│ 히스토그램 양전환 초기...        │
│ ┌─ 근거 ────────────────────┐  │   - rationale (list)
│ │ • MACD 히스토그램 양전환    │  │
│ │ • 20/60 이평선 골든크로스   │  │
│ │ • 거래량 +12%              │  │
│ ├─ 리스크 ──────────────────┤  │   - risks (list)
│ │ ⚠ 실적 발표 D-3            │  │
│ │ ⚠ RSI 58 과매수 임계 접근   │  │
│ └────────────────────────────┘  │
│ ℹ 본 시그널은 매수/매도 권유가   │   - 하단 면책
│    아닙니다. 투자 판단과 책임은  │
│    사용자 본인에게 있습니다.     │
├────────────────────────────────┤
│ 📰 뉴스 (한국어 요약)           │ ← NewsPanel
│ ┌──────────────────────────────┐│
│ │ Reuters · 2h 전               ││
│ │ 애플, 1분기 아이폰 판매...    ││
│ │ 애널리스트들은... (3줄 요약)  ││
│ │ [원문 보기 →]                 ││
│ └──────────────────────────────┘│
│ ... (최대 10개)                 │
├────────────────────────────────┤
│ Footer: 이용약관 · 프라이버시    │ ← DisclaimerFooter (전역)
│ · 면책 · 본 서비스는 ...        │
└────────────────────────────────┘
```

### 5.2 Screen Layout — 시장 대시보드 (데스크톱 1200px)

```
┌─ DESKTOP (1200px) ──────────────────────────────────────────────┐
│ Header · 검색바(항시) · 링크(시장/용어사전/법적고지)              │
├─────────────────────────────────────────────────────────────────┤
│ ⚠️ 투자 자문 아님, 참고용 (고정 배너)                            │
├─────────────────────────────────────────────────────────────────┤
│ ┌───────────────┬────────────────┬────────────────┬───────────┐ │
│ │ S&P 500       │ NASDAQ         │ DOW            │ VIX       │ │
│ │ 5234 +0.52%   │ 16890 +0.78%   │ 39100 +0.21%   │ 15.2 -3.1%│ │
│ └───────────────┴────────────────┴────────────────┴───────────┘ │
│ ┌───────────────┬────────────────┐                              │
│ │ USD/KRW       │ UST 10Y        │                              │
│ │ 1,380 +0.12%  │ 4.52%  -0.03%  │                              │
│ └───────────────┴────────────────┘                              │
├─────────────────────────────────────────────────────────────────┤
│ ┌─ 오르는 종목 ─┬─ 내리는 종목 ──┬─ 거래 활발 ──────┐           │
│ │ NVDA +5.2%   │ PLTR -3.8%    │ TSLA 5억주       │           │
│ │ AMD  +3.1%   │ META -2.4%    │ AAPL 3억주       │           │
│ │ TSM  +2.7%   │ GOOGL-1.9%    │ SPY  2억주       │           │
│ └──────────────┴───────────────┴──────────────────┘           │
├─────────────────────────────────────────────────────────────────┤
│ 📰 시장 뉴스 (한국어 요약, 10건)                                 │
│ ...                                                             │
└─────────────────────────────────────────────────────────────────┘
```

### 5.3 User Flow

```
① 랜딩 (/) ──▶ 검색창 타이핑 "aap" ──▶ 자동완성 리스트 (top 10)
                                          │
                                          ▼
                               ② /stock/AAPL (SSR/ISR 인기 10종목)
                                          │
                    ┌─────────────────────┼────────────────────────┐
                    ▼                     ▼                        ▼
          ③ 차트 타임프레임 변경   ④ AI 시그널 상세 펼침   ⑤ 뉴스 원문 링크 클릭
                    │                     │                        │
                    └──────────┬──────────┘                        ▼
                               ▼                         외부 사이트로 이동
                     ⑥ /market 시장 전체 스캔
                               │
                               ▼
                     ⑦ 다른 종목 검색 (반복)
```

### 5.4 Component List

| Component | Location | Responsibility |
|-----------|----------|----------------|
| `Header` | `src/components/layout/header.tsx` | 검색바·메뉴·다크모드 토글 |
| `DisclaimerBanner` | `src/components/legal/disclaimer-banner.tsx` | 종목 상세 상단 고정 면책 |
| `DisclaimerFooter` | `src/components/legal/disclaimer-footer.tsx` | 전역 하단 면책 |
| `SearchBox` | `src/features/search/search-box.tsx` | 자동완성 (React Query debounce 300ms) |
| `StockHeader` | `src/features/stock-detail/stock-header.tsx` | 티커·이름·가격·변동 |
| `TimeFrameTabs` | `src/features/stock-detail/time-frame-tabs.tsx` | 6종 시간프레임 |
| `ChartPanel` | `src/features/stock-detail/chart/chart-panel.tsx` | TradingView Lightweight Charts wrapper |
| `IndicatorsPanel` | `src/features/stock-detail/indicators/indicators-panel.tsx` | RSI/MACD/BB/MA 카드 + 툴팁 |
| `AiSignalPanel` | `src/features/stock-detail/ai-signal/ai-signal-panel.tsx` | 배지·요약·rationale·risks |
| `NewsPanel` | `src/features/stock-detail/news/news-panel.tsx` | 뉴스 리스트 (한국어 요약) |
| `MarketIndicesCard` | `src/features/market-dashboard/market-indices-card.tsx` | SPX/NDX/DJI/VIX |
| `MoversBoard` | `src/features/market-dashboard/movers-board.tsx` | 오르는/내리는/활발 |
| `PopularTickersRibbon` | `src/features/market-dashboard/popular-tickers-ribbon.tsx` | 랜딩 상단 인기 종목 |
| `GlossaryPage` | `src/app/glossary/page.tsx` | 용어사전 정적 (F9, Next.js 예약 파일) |
| `LegalPages` | `src/app/legal/*` | 면책·약관·프라이버시 (static) |

---

## 6. Error Handling

### 6.1 Error Code Definition

| Code | HTTP | When | User Handling |
|------|:---:|------|---------------|
| `INVALID_TICKER` | 400 | ticker 정규식 불일치 | "올바른 티커를 입력해주세요" |
| `TICKER_NOT_FOUND` | 404 | 외부 검색 0건 | 404 페이지 + 검색창 |
| `UPSTREAM_RATE_LIMIT` | 429 | Finnhub/Gemini RL | toast "잠시 후 다시 시도" |
| `UPSTREAM_TIMEOUT` | 504 | 외부 타임아웃 | partial response + 블록별 재시도 버튼 |
| `UPSTREAM_UNAVAILABLE` | 503 | 모든 외부 장애 | 전체 다운 페이지 + 상태 링크 |
| `LLM_VALIDATION_FAILED` | 502 | 금지어·스키마 반복 실패 | partial response (`aiSignal: null`) |
| `INTERNAL_ERROR` | 500 | 예기치 못한 예외 | toast + 에러 ID 표시 |
| `FORBIDDEN_CONTENT` | 500 | LegalGuard 응답 차단 | partial response + 로그 |

### 6.2 Error Response Format

```json
{
  "error": {
    "code": "UPSTREAM_TIMEOUT",
    "message": "외부 데이터 제공자 응답이 지연되어 일부 정보를 표시하지 못했습니다.",
    "details": { "block": "aiSignal", "provider": "gemini" },
    "requestId": "req_abc123",
    "timestamp": "2026-04-13T20:45:01Z"
  }
}
```

### 6.3 Resilience Strategy

| Layer | Strategy |
|-------|----------|
| HTTP Client | Resilience4j: `timeout 3s` (search/quote/profile), `8s` (candles/news), `10s` (Gemini) |
| Circuit Breaker | `failureRateThreshold 50%`, `slowCallDurationThreshold 5s`, `slidingWindowSize 20` |
| Retry | Exponential backoff: 300ms → 900ms → 2.7s, max 3 (외부 5xx/타임아웃만) |
| Bulkhead | 가상 스레드 기반, per-provider semaphore (Finnhub 30, Gemini 10) |
| Fallback | 캐시 stale 반환(TTL 지났지만 만료되지 않은 부드러운 stale → 1h까지) → partial response |

---

## 7. Security Considerations

- [x] **입력 검증** — ticker 정규식, query 길이 제한(≤ 20), SQL Injection 방어(Prepared Statement만), XSS 방어(React 자동 escape + `dangerouslySetInnerHTML` 금지)
- [x] **비밀 관리** — `*_API_KEY` 는 서버 환경변수만, FE 번들에 포함 금지. Vercel/Fly secrets 사용
- [x] **프롬프트 인젝션 방어** — 사용자 입력(ticker)은 enum 화이트리스트 + 정규식 검증. 프롬프트 템플릿에 사용자 자유 문자열 주입 금지
- [x] **Rate Limiting** — `/api/v1/*` IP 기반 60 req/min (버킷), `/ai-signal` 은 IP 기반 10 req/min (LLM 비용 보호)
- [x] **CORS** — FE 도메인만 화이트리스트 (`aistockadvisor.app` + `localhost:3000`)
- [x] **CSP** — `default-src 'self'`, `img-src 'self' https://*.finnhub.io https://logo.clearbit.com data:`, `connect-src 'self' https://api.aistockadvisor.app`
- [x] **HTTPS 강제** — HSTS 1y, Fly.io/Vercel 자동 TLS
- [x] **로그 PII 금지** — ticker, requestId, latency, cacheHit 만. 사용자 IP 는 hash (SHA256) 저장
- [x] **의존성 모니터링** — Dependabot + `npm audit` / `./gradlew dependencyCheckAnalyze` CI 게이트

### Legal Security (프로젝트 고유)

- [x] **Forbidden terms grep CI** — GitHub Actions 에서 `apps/`, 프롬프트 파일, 정적 카피 전수 스캔. `매수|매도|추천|보장|확실` 발견 시 CI fail
- [x] **LegalGuard Filter** — 모든 `/api/v1/**` 응답을 ObjectMapper 로 직렬화 직후 문자열 전수 스캔. 발견 시 로그 + `FORBIDDEN_CONTENT` 에러
- [x] **면책 커버리지 Playwright** — E2E 에서 모든 페이지에 `data-testid="disclaimer"` 존재 검증

---

## 8. Test Plan

### 8.1 Test Scope

| Type | Target | Tool | Phase |
|------|--------|------|:---:|
| **Unit (FE)** | 포맷터·훅·컴포넌트 로직 | Vitest + Testing Library | 1+ |
| **Unit (BE)** | Service 비즈니스 로직, Validator, PromptBuilder | JUnit 5 + AssertJ + Mockito | 1+ |
| **Integration (BE)** | Controller + Repository + Redis (Testcontainers) | Spring Boot Test + Testcontainers | 2+ |
| **Contract (외부)** | Finnhub/Gemini client 모의 (WireMock) | WireMock | 2+ |
| **E2E** | 검색 → 상세 → AI 시그널 → 뉴스 happy path, 면책 커버리지 | Playwright | 3+ |
| **Visual Regression** | 종목 상세 / 시장 대시보드 / 404 | Playwright screenshots | 3+ |
| **Performance** | 주요 API P95 측정 | k6 / Autocannon | 3+ |
| **LLM Quality** | 10종목 수동 blind review (4.0/5.0) | Spreadsheet | 2+ |

### 8.2 Test Cases (Key)

- [ ] **Happy**: `GET /api/v1/stocks/AAPL/detail` → 200, 4 블록 모두 non-null, disclaimer 포함
- [ ] **Partial**: Gemini mock 타임아웃 → 200 with `partial: true`, `aiSignal: null`, errors 배열
- [ ] **Invalid ticker**: `GET /api/v1/stocks/!!/detail` → 400 `INVALID_TICKER`
- [ ] **Ticker not found**: `GET /api/v1/stocks/ZZZZZ/detail` (Finnhub 빈 결과) → 404 `TICKER_NOT_FOUND`
- [ ] **Forbidden content**: Gemini mock 이 "매수 추천합니다" 반환 → 1회 재시도 → 2회 실패 → `aiSignal: null` + 로그
- [ ] **Cache hit**: 동일 ticker 연속 2회 호출 시 2번째 응답 헤더 `X-Cache: HIT` + 시세 변동 없음
- [ ] **Rate limit**: IP 기반 60 req/min 초과 시 429
- [ ] **Legal grep CI**: 코드에 `"매수 추천"` 추가 → CI fail
- [ ] **Disclaimer coverage**: Playwright 가 `/`, `/stock/AAPL`, `/market`, `/glossary`, `/legal/*` 5 페이지 모두에서 `data-testid=disclaimer` 발견
- [ ] **Mobile layout**: 375px 뷰포트에서 종목 상세 차트 가로 스크롤 없음
- [ ] **SEO**: `/stock/AAPL` HTML에 `<title>AAPL — Apple Inc. | AI Stock Advisor</title>` SSR 주입

---

## 9. Clean Architecture

### 9.1 Layer Structure — Frontend (apps/web)

| Layer | Responsibility | Location |
|-------|---------------|----------|
| **Presentation** | Next.js 페이지, 레이아웃, 컴포넌트, 훅(UI state) | `src/app/`, `src/components/`, `src/features/*/components/`, `src/features/*/hooks/` |
| **Application** | React Query 훅 (`useStockDetail`, `useMarketSnapshot`), 도메인 변환 로직 | `src/features/*/api/`, `src/features/*/hooks/use*.ts` |
| **Domain** | 타입 정의, 공통 enum, 순수 유틸 (포맷터) | `src/types/`, `src/lib/format/` |
| **Infrastructure** | BE API 클라이언트 (fetch wrapper), 환경변수 어댑터 | `src/lib/api/`, `src/lib/env.ts` |

### 9.2 Layer Structure — Backend (apps/api)

| Layer | Responsibility | Location |
|-------|---------------|----------|
| **Presentation** | `@RestController`, DTO (request/response) | `com.aistockadvisor.{domain}.web` |
| **Application** | Service (도메인 로직 오케스트레이션), RAG 파이프라인 체인 | `com.aistockadvisor.{domain}.service` |
| **Domain** | Entity, VO, Enum (`Signal`, `Confidence`, `TimeFrame`) | `com.aistockadvisor.{domain}.domain` |
| **Infrastructure** | Client (Finnhub/Gemini/Alpha), Redis adapter, JPA repo, Flyway | `com.aistockadvisor.{domain}.infra` |

### 9.3 Dependency Rules

```
Presentation ──▶ Application ──▶ Domain ◀── Infrastructure
                      │                            ▲
                      └────────────────────────────┘

불변 규칙:
- Domain 은 어떤 외부 라이브러리도 import 하지 않는다 (순수 Java/TS)
- Infrastructure 는 Domain 타입만 반환 (Finnhub raw JSON 은 infra 내부에서 매핑 후 전달)
- Controller 는 Service 만 주입, Repository/Client 직접 주입 금지
- FE 컴포넌트는 api/ 레이어만 호출, fetch 직접 사용 금지
```

### 9.4 This Feature's Layer Assignment (MVP 핵심 컴포넌트)

| Component | Layer | Location (FE) |
|-----------|-------|---------------|
| `StockDetailPage` | Presentation | `src/app/stock/[ticker]/page.tsx` |
| `ChartPanel` | Presentation | `src/features/stock-detail/chart/chart-panel.tsx` |
| `useStockDetail()` hook | Application | `src/features/stock-detail/hooks/use-stock-detail.ts` |
| `StockDetail` type | Domain | `src/types/stock.ts` |
| `stocksApi.getDetail()` | Infrastructure | `src/lib/api/stocks.ts` |

| Component | Layer | Location (BE) |
|-----------|-------|---------------|
| `StockController` | Presentation | `com.aistockadvisor.stock.web.StockController` |
| `StockDetailService` | Application | `com.aistockadvisor.stock.service.StockDetailService` |
| `AiSignalService` | Application | `com.aistockadvisor.ai.service.AiSignalService` |
| `ContextAssembler` | Application | `com.aistockadvisor.ai.service.rag.ContextAssembler` |
| `ResponseValidator` | Application | `com.aistockadvisor.ai.service.rag.ResponseValidator` |
| `Signal` / `Confidence` enum | Domain | `com.aistockadvisor.ai.domain` |
| `FinnhubClient` / `GeminiClient` | Infrastructure | `com.aistockadvisor.{stock,ai}.infra.client` |
| `RedisCacheAdapter` | Infrastructure | `com.aistockadvisor.common.infra.cache` |
| `LegalGuardFilter` | Infrastructure (Servlet filter) | `com.aistockadvisor.legal.infra` |

---

## 10. Coding Convention Reference

CLAUDE.md 의 규칙을 승계. 본 문서에서는 **프로젝트 고유 추가 규칙**만 기술:

### 10.1 Naming Conventions (추가)

| Target | Rule | Example |
|--------|------|---------|
| FE API 호출 모듈 | `{domain}Api` 객체 + `get*`/`post*` 메서드 | `stocksApi.getDetail(ticker)` |
| FE React Query 훅 | `use{Domain}{Action}` | `useStockDetail`, `useMarketSnapshot` |
| BE DTO | `*Request` / `*Response` | `StockDetailResponse`, `SearchRequest` |
| BE Enum | PascalCase + 상수는 UPPER_SNAKE | `Signal.BULLISH` |
| BE Flyway 마이그레이션 | `V{n}__{snake_description}.sql` | `V1__init.sql`, `V2__add_bookmarks.sql` |

### 10.2 Import Order — 프로젝트 절대경로

```typescript
// 1. External
import { useQuery } from '@tanstack/react-query';

// 2. Internal (@/ alias)
import { stocksApi } from '@/lib/api/stocks';
import { DisclaimerBanner } from '@/components/legal/disclaimer-banner';

// 3. Relative
import { ChartPanel } from './chart/chart-panel';

// 4. Types
import type { StockDetail } from '@/types/stock';

// 5. Styles (없음 — Tailwind 클래스만)
```

### 10.3 Environment Variables (MVP 확정 목록)

§ Plan §7.3 참조. Design 추가 사항:
- `application-local.yml` / `application-prod.yml` 분리
- `application-secret.yml` (.gitignore) 로 로컬 비밀 주입
- GitHub Actions 는 `secrets.*` 주입

### 10.4 This Feature's Conventions

| Item | Convention Applied |
|------|-------------------|
| Component naming | PascalCase (StockHeader, AiSignalPanel) |
| File organization | `src/features/<domain>/...` 기능 단위 |
| State management | React Query (server) + Zustand (`useUiStore` for timeframe/ theme) |
| Error handling | BE `@ControllerAdvice` 통합 · FE global `ErrorBoundary` + toast |
| LLM prompts | `src/main/resources/prompts/{name}.{version}.txt` (버전 고정) |
| Forbidden terms | `src/main/resources/legal/forbidden-terms.json` (CI + 런타임 공용) |

---

## 11. Implementation Guide

### 11.1 File Structure

```
apps/web/
├── src/
│   ├── app/
│   │   ├── layout.tsx              # 전역 DisclaimerFooter, Header
│   │   ├── page.tsx                # 랜딩 (시장 대시보드 요약 + 인기종목 리본)
│   │   ├── stock/[ticker]/page.tsx # 종목 상세 (SSR/ISR for 인기 10종목)
│   │   ├── market/page.tsx         # 시장 대시보드 전체
│   │   ├── glossary/page.tsx       # F9 용어사전
│   │   ├── legal/
│   │   │   ├── terms/page.tsx
│   │   │   ├── privacy/page.tsx
│   │   │   └── disclaimer/page.tsx
│   │   ├── robots.ts
│   │   └── sitemap.ts
│   ├── components/
│   │   ├── layout/ (header.tsx, footer.tsx)
│   │   ├── legal/ (disclaimer-banner.tsx, disclaimer-footer.tsx)
│   │   └── ui/ (button.tsx, card.tsx, tabs.tsx, tooltip.tsx — headless + Tailwind)
│   ├── features/
│   │   ├── search/
│   │   ├── stock-detail/
│   │   │   ├── chart/
│   │   │   ├── indicators/
│   │   │   ├── ai-signal/
│   │   │   └── news/
│   │   └── market-dashboard/
│   ├── lib/
│   │   ├── api/ (stocks.ts, market.ts, legal.ts)
│   │   ├── format/ (currency.ts, percent.ts, date.ts)
│   │   └── env.ts
│   └── types/ (stock.ts, market.ts, common.ts)
├── public/
├── package.json
├── tsconfig.json
├── tailwind.config.ts
├── next.config.mjs
└── .env.example

apps/api/
├── src/main/java/com/aistockadvisor/
│   ├── AiStockAdvisorApplication.java
│   ├── stock/
│   │   ├── web/StockController.java
│   │   ├── service/{StockDetailService,SearchService,QuoteService,IndicatorService}.java
│   │   ├── domain/{StockProfile,Quote,Candle,IndicatorSnapshot,TimeFrame}.java
│   │   └── infra/client/{FinnhubClient,AlphaVantageClient}.java
│   ├── news/
│   │   ├── web/NewsController.java
│   │   ├── service/NewsService.java
│   │   ├── domain/NewsItem.java
│   │   └── infra/client/ (shared Finnhub)
│   ├── ai/
│   │   ├── web/AiSignalController.java
│   │   ├── service/{AiSignalService}.java
│   │   ├── service/rag/{ContextAssembler,PromptBuilder,ResponseValidator}.java
│   │   ├── domain/{AiSignal,Signal,Confidence}.java
│   │   └── infra/client/GeminiClient.java
│   ├── market/
│   │   ├── web/MarketController.java
│   │   ├── service/MarketDashboardService.java
│   │   └── domain/MarketSnapshot.java
│   ├── legal/
│   │   ├── web/LegalController.java
│   │   ├── service/DisclaimerService.java
│   │   ├── infra/LegalGuardFilter.java
│   │   └── domain/{Disclaimer,ForbiddenTerms}.java
│   ├── cache/
│   │   └── RedisCacheAdapter.java
│   └── common/
│       ├── error/{GlobalExceptionHandler,ErrorResponse}.java
│       ├── logging/StructuredLoggingFilter.java
│       ├── ratelimit/BucketedRateLimitFilter.java
│       └── security/SecurityConfig.java
├── src/main/resources/
│   ├── application.yml
│   ├── application-local.yml.example
│   ├── db/migration/V1__init.sql
│   ├── prompts/ai-signal.v1.0.txt
│   └── legal/forbidden-terms.json
├── src/test/... (JUnit + Testcontainers)
├── build.gradle.kts
├── Dockerfile
└── .env.example
```

### 11.2 Implementation Order (v0.1 → v1.0)

**v0.1 (Week 1~4, Phase 1 Single Stock Pipeline):**
1. [ ] `apps/api` Spring Initializr 초기화 (Boot 3.2+, Java 21, Gradle Kotlin DSL, JPA, Flyway, Actuator, Validation)
2. [ ] `apps/web` `pnpm create next-app@latest` (App Router, TS, Tailwind, ESLint)
3. [ ] `.env.example`, `application.yml`, CI GitHub Actions skeleton (build + lint)
4. [ ] Flyway V1 마이그레이션 (3 테이블) + Supabase 연결
5. [ ] `FinnhubClient` + `RedisCacheAdapter` + `SearchService` + `QuoteService` + `StockProfile` GET API
6. [ ] `IndicatorService` (ta4j) + `/indicators` API + 툴팁 JSON
7. [ ] FE `SearchBox` + `/stock/[ticker]` 기본 레이아웃 + `ChartPanel` (TradingView Lightweight)
8. [ ] FE `IndicatorsPanel` + 툴팁 + `StockHeader` + `TimeFrameTabs`
9. [ ] `/api/v1/stocks/{ticker}/detail` 통합 엔드포인트 (AI/뉴스 null 허용)
10. [ ] `DisclaimerBanner` + `DisclaimerFooter` + `/legal/*` 정적 페이지 초안
11. [ ] ✅ Gate: AAPL 검색 → 차트/지표/원문 뉴스(영어 링크만) 표시

**v0.2 (Week 5~7, Phase 2 AI):**
12. [ ] `GeminiClient` + `ContextAssembler` + `PromptBuilder` + `ResponseValidator`
13. [ ] `AiSignalService` + `/ai-signal` API + ai_signal_history 저장
14. [ ] `NewsService` (Finnhub 수집) + Gemini 한국어 요약 + `/news` API
15. [ ] FE `AiSignalPanel` + `NewsPanel` + `/detail` 통합
16. [ ] `LegalGuardFilter` + forbidden-terms.json + 런타임 스캔
17. [ ] CI forbidden-terms grep 스크립트
18. [ ] ✅ Gate: 10종목 수동 파일럿 품질 4.0/5.0

**v0.3 (Week 8~9, Phase 3 Market):**
19. [ ] `MarketDashboardService` + `/market/snapshot` API
20. [ ] FE `/market` 페이지 + `MarketIndicesCard` + `MoversBoard` + `PopularTickersRibbon`
21. [ ] `/` 랜딩 통합 + 인기 10종목 ISR (1h) + sitemap.ts / robots.ts
22. [ ] F9 용어사전 정적 페이지
23. [ ] ✅ Gate: "/" 에서 "오늘 시장 어때?" 질문에 답됨

**v1.0 (Week 10, MVP Public):**
24. [ ] 약관/프라이버시/면책 상세 페이지 최종본
25. [ ] Playwright E2E 핵심 시나리오 + 면책 커버리지
26. [ ] Lighthouse ≥ 85 (mobile) / ≥ 90 (desktop) on 10종목
27. [ ] Dependabot + npm audit CI 게이트 green
28. [ ] 변호사 30분 리뷰 통과
29. [ ] Vercel / Fly.io prod 배포 + 도메인 + HSTS
30. [ ] 커뮤니티 공개 포스트 1건 이상
31. [ ] ✅ Gate: v1.0 태그 + MVP Public 선언

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 0.1 | 2026-04-13 | Initial draft. Plan `mvp.plan.md` 승계 + RAG 파이프라인·금지어 가드·캐시 키·API 스펙·UI 와이어·구현 순서 세분화 | wonseok-han |
