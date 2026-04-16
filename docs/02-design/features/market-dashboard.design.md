# market-dashboard Design Document

> **Summary**: 메인 페이지를 시장 대시보드로 전환. 주요 지수·VIX·환율·시장 뉴스·급등락 종목을 한 화면에 제공하여 "오늘 시장 분위기"를 즉시 파악하게 한다.
>
> **Project**: AI Stock Advisor
> **Version**: 0.1 (Phase 3)
> **Author**: wonseok-han
> **Date**: 2026-04-15
> **Status**: Draft
> **Planning Doc**: [market-dashboard.plan.md](../../01-plan/features/market-dashboard.plan.md)

---

## Executive Summary

| 관점 | 설명 |
|---|---|
| **Problem** | 현재 메인(`/`)이 검색 박스만 있어 "오늘 시장이 어때?"라는 가장 기본적인 질문에 답하지 못함 |
| **Solution** | 주요 지수·VIX·환율·시장 뉴스·급등락 종목을 한 화면에 모아 시장 전체 스냅샷 제공 |
| **Function UX Effect** | 앱 진입 즉시 시장 분위기 파악 → 관심 종목 클릭 → 종목 상세 연결, 체류 시간 증가 |
| **Core Value** | "첫 화면이 오늘 시장 분위기에 대한 답이 된다" — 로드맵 Phase 3 완료 조건 직접 달성 |

---

## 1. Overview

### 1.1 Design Goals

1. **단일 API 호출로 지수/VIX/환율 조합** — FE 가 3~5개 개별 호출하지 않고 BE `/market/overview` 1회로 전부 수신
2. **시장 뉴스 한국어 번역** — 기존 `NewsTranslator` 재사용, 시장 일반 뉴스(category=general)에 적용
3. **인기 종목 풀 기반 급등/급락** — Finnhub 무료에 movers API 없음 → 인기 종목 30개 quote 일괄 조회 후 변동률 정렬
4. **부분 실패 허용** — 지수 실패해도 뉴스는 표시, 뉴스 실패해도 지수는 표시. 각 섹션 독립 로딩
5. **캐시 최우선** — 외부 API rate limit 보호. overview 5분, news/movers 15분 TTL

### 1.2 Design Principles

- **기존 인프라 최대 활용** — `FinnhubClient`, `RedisCacheAdapter`, `NewsTranslator`, `apiFetch` 등 검증된 컴포넌트 재사용
- **새 패키지 격리** — `com.aistockadvisor.market` 패키지로 시장 대시보드 도메인 분리. 기존 `stock`/`news` 패키지와 의존만 하고 코드 수정 최소화
- **FE 독립 섹션 로딩** — 3개 React Query hook 이 독립적으로 fetch. 한 섹션 에러가 다른 섹션에 전파되지 않음

---

## 2. Architecture

### 2.1 Component Diagram

```
┌─────────────────────────────────────────────────────────┐
│                    CLIENT (Browser)                      │
│  ┌───────────────────────────────────────────────────┐  │
│  │  app/page.tsx (메인 페이지 — 대시보드 통합)         │  │
│  │   ├─ SearchBox (기존)                              │  │
│  │   ├─ MarketOverview (지수 + VIX + 환율 카드)       │  │
│  │   ├─ MarketMovers (급등/급락 종목 테이블)          │  │
│  │   └─ MarketNews (시장 뉴스 피드)                   │  │
│  │                                                     │  │
│  │  hooks: useMarketOverview, useMarketMovers,         │  │
│  │         useMarketNews (React Query)                 │  │
│  └────────────────────┬──────────────────────────────┘  │
└───────────────────────┼──────────────────────────────────┘
                        │ HTTPS · /api/v1/market/*
                        ▼
┌─────────────────────────────────────────────────────────┐
│                 BACKEND (Spring Boot)                     │
│  ┌───────────────────────────────────────────────────┐  │
│  │ MarketController                                    │  │
│  │   GET /market/overview → MarketOverviewService      │  │
│  │   GET /market/news     → MarketNewsService          │  │
│  │   GET /market/movers   → MarketMoversService        │  │
│  └────────────────────┬──────────────────────────────┘  │
│  ┌────────────────────┴──────────────────────────────┐  │
│  │ Services                                            │  │
│  │  ├─ MarketOverviewService                           │  │
│  │  │   └─ FinnhubClient.quote() × 5 (지수+VIX+환율)  │  │
│  │  ├─ MarketNewsService                               │  │
│  │  │   └─ FinnhubMarketNewsClient + NewsTranslator    │  │
│  │  └─ MarketMoversService                             │  │
│  │      └─ FinnhubClient.quote() × 30 (인기 종목 풀)  │  │
│  │                                                      │  │
│  │  RedisCacheAdapter (overview 5m, news/movers 15m)   │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
          │                         │
          ▼                         ▼
   [Finnhub API]             [Upstash Redis]
```

### 2.2 신규 패키지 구조

```
com.aistockadvisor.market/
  ├── web/
  │   └── MarketController.java           # 3개 엔드포인트
  ├── service/
  │   ├── MarketOverviewService.java      # 지수/VIX/환율 조합
  │   ├── MarketNewsService.java          # 시장 뉴스 + 번역
  │   └── MarketMoversService.java        # 인기 종목 풀 → 변동률 정렬
  ├── domain/
  │   ├── MarketIndex.java                # 개별 지수 DTO
  │   ├── MarketOverviewResponse.java     # overview 조합 응답
  │   ├── MarketMover.java                # 급등/급락 종목 DTO
  │   ├── MarketMoversResponse.java       # movers 응답 (gainers + losers)
  │   └── MarketNewsItem.java             # 시장 뉴스 아이템 DTO
  └── infra/
      ├── FinnhubMarketNewsClient.java    # Finnhub /news?category=general
      └── PopularTickerPool.java          # 인기 종목 30개 상수 관리
```

```
apps/web/src/features/market-dashboard/
  ├── market-overview.tsx                 # 지수 카드 그리드
  ├── market-news.tsx                     # 뉴스 피드 리스트
  ├── market-movers.tsx                   # 급등/급락 2-column 테이블
  └── hooks/
      ├── use-market-overview.ts          # React Query hook
      ├── use-market-news.ts
      └── use-market-movers.ts

apps/web/src/lib/api/market.ts            # apiFetch 래퍼 함수들
apps/web/src/types/market.ts              # 타입 정의
```

---

## 3. Data Model

### 3.1 BE Domain DTOs (Java Records)

#### MarketIndex

```java
public record MarketIndex(
    String symbol,      // "SPX", "IXIC", "DJI", "VIX"
    String name,        // "S&P 500", "Nasdaq", "Dow Jones", "VIX"
    BigDecimal price,
    BigDecimal change,
    BigDecimal changePercent,
    OffsetDateTime updatedAt
) {}
```

#### MarketOverviewResponse

```java
public record MarketOverviewResponse(
    List<MarketIndex> indices,    // S&P500, Nasdaq, Dow, VIX
    BigDecimal usdKrw,           // USD/KRW 환율
    BigDecimal usdKrwChange,     // 환율 전일 대비 변동
    OffsetDateTime updatedAt,
    String disclaimer
) {}
```

#### MarketMover

```java
public record MarketMover(
    String ticker,
    String name,
    BigDecimal price,
    BigDecimal change,
    BigDecimal changePercent,
    BigDecimal volume
) {}
```

#### MarketMoversResponse

```java
public record MarketMoversResponse(
    List<MarketMover> gainers,   // 상위 5개 (changePercent DESC)
    List<MarketMover> losers,    // 하위 5개 (changePercent ASC)
    int poolSize,                // 현재 모니터링 풀 크기
    OffsetDateTime updatedAt,
    String disclaimer
) {}
```

#### MarketNewsItem

```java
public record MarketNewsItem(
    long id,
    String source,
    String sourceUrl,
    String titleEn,
    String titleKo,          // nullable (번역 실패 시)
    String summaryKo,        // nullable
    long publishedAt,        // epoch seconds
    String disclaimer
) {}
```

### 3.2 FE Types (TypeScript)

```typescript
// types/market.ts

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
  usdKrw: number;
  usdKrwChange: number;
  updatedAt: string;
  disclaimer: string;
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
```

---

## 4. API Specification

### 4.1 GET /api/v1/market/overview

주요 지수(S&P500, Nasdaq, Dow), VIX, USD/KRW 환율을 단일 응답으로 반환.

**Request**: 파라미터 없음

**Response** `200`:

```json
{
  "indices": [
    {
      "symbol": "SPX",
      "name": "S&P 500",
      "price": 5234.18,
      "change": 15.29,
      "changePercent": 0.29,
      "updatedAt": "2026-04-15T15:30:00Z"
    },
    {
      "symbol": "IXIC",
      "name": "Nasdaq",
      "price": 16340.87,
      "change": -42.15,
      "changePercent": -0.26,
      "updatedAt": "2026-04-15T15:30:00Z"
    },
    {
      "symbol": "DJI",
      "name": "Dow Jones",
      "price": 39127.14,
      "change": 123.45,
      "changePercent": 0.32,
      "updatedAt": "2026-04-15T15:30:00Z"
    },
    {
      "symbol": "VIX",
      "name": "VIX",
      "price": 16.42,
      "change": -0.58,
      "changePercent": -3.41,
      "updatedAt": "2026-04-15T15:30:00Z"
    }
  ],
  "usdKrw": 1345.20,
  "usdKrwChange": 2.30,
  "updatedAt": "2026-04-15T15:30:00Z",
  "disclaimer": "본 데이터는 참고용이며, 실시간 시세와 차이가 있을 수 있습니다."
}
```

**Cache**: Redis `market:overview`, TTL 5분

**데이터 소스 전략 (지수 심볼)**:

Finnhub 무료에서 지수 quote 가능 여부가 불확실. 구현 시 다음 우선순위로 시도:

| 순위 | 소스 | 심볼 형태 | 비고 |
|---|---|---|---|
| 1 | Finnhub `/quote` | `^GSPC`, `^IXIC`, `^DJI`, `^VIX` | 무료 지원 시 최적 |
| 2 | Twelve Data `/quote` | `SPX`, `IXIC`, `DJI`, `VIX` | API 키 이미 보유 |
| 3 | Finnhub `/forex/rates` | (환율 전용) | 환율은 별도 경로 |

> **구현 시점에 Finnhub 지수 quote 테스트 후 소스 확정.** Twelve Data fallback 구조로 설계.

**환율 (USD/KRW)**:

- Finnhub: `GET /forex/rates?base=USD&token={key}` → `quote.KRW` 값 사용
- 전일 대비 변동: 당일 rate - 전일 캐시된 rate (없으면 0)

### 4.2 GET /api/v1/market/news

시장 일반 뉴스 상위 10건. 한국어 번역 포함.

**Request**:

| 파라미터 | 타입 | 기본값 | 설명 |
|---|---|---|---|
| `limit` | int | 10 | 최대 10 |

**Response** `200`:

```json
[
  {
    "id": 123456789,
    "source": "Reuters",
    "sourceUrl": "https://reuters.com/article/...",
    "titleEn": "Fed signals potential rate cut in September",
    "titleKo": "연준, 9월 금리 인하 가능성 시사",
    "summaryKo": "제롬 파월 연준 의장이 인플레이션 둔화 추세를 확인하며 9월 금리 인하 가능성을 시사했습니다.",
    "publishedAt": 1713193200,
    "disclaimer": "본 뉴스 번역은 AI에 의한 참고용이며, 원문과 차이가 있을 수 있습니다."
  }
]
```

**Cache**: Redis `market:news`, TTL 15분

**데이터 소스**: Finnhub `GET /news?category=general&token={key}` → 최대 10건 필터링

**번역 전략**:
- 기존 `NewsTranslator` 재사용
- `CompanyNews` record 와 동일한 구조로 Finnhub general news 매핑
- 번역 실패 시 `titleKo`/`summaryKo` = null, FE 에서 영문 원문 fallback 표시

### 4.3 GET /api/v1/market/movers

급등/급락 종목 각 5개.

**Request**: 파라미터 없음

**Response** `200`:

```json
{
  "gainers": [
    {
      "ticker": "NVDA",
      "name": "NVIDIA Corp",
      "price": 875.42,
      "change": 32.15,
      "changePercent": 3.81,
      "volume": 0
    }
  ],
  "losers": [
    {
      "ticker": "INTC",
      "name": "Intel Corp",
      "price": 31.28,
      "change": -1.56,
      "changePercent": -4.75,
      "volume": 0
    }
  ],
  "poolSize": 30,
  "updatedAt": "2026-04-15T15:30:00Z",
  "disclaimer": "변동률은 인기 종목 30개 기준이며, 전체 시장 급등/급락과 다를 수 있습니다."
}
```

**Cache**: Redis `market:movers`, TTL 15분

**데이터 소스 전략**:

```
PopularTickerPool (30개 상수)
  → FinnhubClient.quote(ticker) × 30  (virtual thread 병렬)
  → changePercent 기준 정렬
  → gainers 상위 5, losers 하위 5
```

**인기 종목 풀 (PopularTickerPool)**:

```java
// 시가총액 상위 + 섹터 다양성 기준 선정
public static final List<String> POPULAR_TICKERS = List.of(
    // Mega Cap Tech
    "AAPL", "MSFT", "GOOGL", "AMZN", "NVDA", "META", "TSLA",
    // Semiconductor
    "AMD", "INTC", "AVGO", "QCOM",
    // Finance
    "JPM", "BAC", "GS", "V", "MA",
    // Healthcare
    "UNH", "JNJ", "PFE", "ABBV",
    // Consumer
    "WMT", "COST", "NKE", "DIS",
    // Energy
    "XOM", "CVX",
    // Industrial
    "BA", "CAT",
    // Communication
    "NFLX", "CRM"
);
```

---

## 5. Service Layer Design

### 5.1 MarketOverviewService

```
[MarketOverviewService]
  │
  ├─ RedisCacheAdapter.getOrLoad("market:overview", 5min)
  │     │
  │     └─ (cache miss) ─────┐
  │                           │
  │    ┌──────────────────────┘
  │    │
  │    ├─ fetchIndices()
  │    │   ├─ FinnhubClient.quote("^GSPC")  ── 실패 시 TwelveDataClient fallback
  │    │   ├─ FinnhubClient.quote("^IXIC")
  │    │   ├─ FinnhubClient.quote("^DJI")
  │    │   └─ FinnhubClient.quote("^VIX")
  │    │   (virtual thread 병렬 실행, 개별 실패 허용)
  │    │
  │    ├─ fetchExchangeRate()
  │    │   └─ Finnhub /forex/rates?base=USD → KRW 추출
  │    │
  │    └─ 조합 → MarketOverviewResponse
  │
  └─ 반환
```

**부분 실패 처리**:
- 개별 지수 quote 실패 → 해당 지수만 리스트에서 제외, 나머지는 정상 반환
- 환율 실패 → `usdKrw` = null, FE 에서 "데이터 없음" 표시
- 전체 실패 → `BusinessException(UPSTREAM_UNAVAILABLE)` throw → 503

**Twelve Data fallback 패턴**:

```java
private MarketIndex fetchIndex(String finnhubSymbol, String twelveName, String displayName) {
    try {
        Quote q = finnhubClient.quote(finnhubSymbol);
        if (q != null) return toMarketIndex(finnhubSymbol, displayName, q);
    } catch (BusinessException ex) {
        log.warn("finnhub index {} failed, trying twelvedata", finnhubSymbol);
    }
    // Twelve Data fallback
    try {
        Quote q = twelveDataClient.quote(twelveName);
        if (q != null) return toMarketIndex(twelveName, displayName, q);
    } catch (BusinessException ex) {
        log.warn("twelvedata index {} also failed", twelveName);
    }
    return null; // 이 지수는 응답에서 제외
}
```

### 5.2 MarketNewsService

```
[MarketNewsService]
  │
  ├─ RedisCacheAdapter.getOrLoad("market:news", 15min)
  │     │
  │     └─ (cache miss) ─────┐
  │                           │
  │    ┌──────────────────────┘
  │    │
  │    ├─ FinnhubMarketNewsClient.fetchGeneralNews(limit=10)
  │    │   └─ GET /news?category=general&minId=0&token={key}
  │    │
  │    ├─ for each news item:
  │    │   └─ NewsTranslator.translate(companyNews)
  │    │       ├─ 성공 → titleKo, summaryKo 채움
  │    │       └─ 실패 → titleKo=null, summaryKo=null (영문 원문 유지)
  │    │
  │    └─ List<MarketNewsItem> 조합
  │
  └─ 반환
```

**기존 코드 재사용**:
- `NewsTranslator.translate(CompanyNews)` — 입력이 `CompanyNews` record 이므로, Finnhub general news 응답을 동일 record 으로 매핑하면 그대로 사용 가능
- `FinnhubMarketNewsClient` 는 기존 `FinnhubNewsClient` 와 유사하되, `/news?category=general` 엔드포인트 사용

**NewsTranslator 재사용 시 주의**:
- 기존 `CompanyNews` record: `(id, datetime, headline, source, summary, url, category, related)`
- Finnhub general news 응답도 동일 필드 → 별도 record 불필요, `CompanyNews` 그대로 역직렬화 가능

**DB 캐시 미사용** (기존 `NewsService`와 차이):
- 종목별 뉴스(`NewsService`)는 DB(`news_raw`) 24h 캐시 + LLM 번역 결과 저장
- 시장 뉴스는 Redis 15분 캐시만 사용 (DB 저장 불필요 — 시장 뉴스는 빠르게 교체되므로)
- 번역 결과는 Redis 캐시에 포함되어 TTL 내 재사용

### 5.3 MarketMoversService

```
[MarketMoversService]
  │
  ├─ RedisCacheAdapter.getOrLoad("market:movers", 15min)
  │     │
  │     └─ (cache miss) ─────┐
  │                           │
  │    ┌──────────────────────┘
  │    │
  │    ├─ PopularTickerPool.POPULAR_TICKERS  (30개)
  │    │
  │    ├─ for each ticker (virtual thread 병렬):
  │    │   ├─ FinnhubClient.quote(ticker) → Quote
  │    │   └─ 실패 시 → skip (해당 종목 제외)
  │    │
  │    ├─ 성공한 quote 들을 changePercent 기준 정렬
  │    │   ├─ gainers: 상위 5개 (changePercent > 0, DESC)
  │    │   └─ losers: 하위 5개 (changePercent < 0, ASC)
  │    │
  │    ├─ 종목명은 기존 StockProfileService 캐시 활용 (24h TTL)
  │    │
  │    └─ MarketMoversResponse 조합
  │
  └─ 반환
```

**병렬 호출 구현** (Virtual Threads):

```java
// Java 21 virtual threads + structured concurrency 개념
List<Quote> quotes = PopularTickerPool.POPULAR_TICKERS.parallelStream()
    .map(ticker -> {
        try {
            return finnhubClient.quote(ticker);
        } catch (Exception ex) {
            log.debug("movers quote failed ticker={}", ticker);
            return null;
        }
    })
    .filter(Objects::nonNull)
    .filter(q -> q.changePercent() != null)
    .toList();
```

**종목명 조회**:
- `StockProfileService.getProfile(ticker)` → 기존 Redis 24h 캐시에서 `name` 조회
- profile 캐시 miss 시 Finnhub profile2 호출 (이미 구현되어 있음)
- profile 도 실패 시 → `name` = ticker 그대로 사용

---

## 6. Cache Strategy

### 6.1 Redis Key 설계

| Key | Value Type | TTL | 갱신 주기 |
|---|---|---|---|
| `market:overview` | `MarketOverviewResponse` JSON | 5분 | 요청 시 cache miss 인 경우 |
| `market:news` | `List<MarketNewsItem>` JSON | 15분 | 요청 시 cache miss 인 경우 |
| `market:movers` | `MarketMoversResponse` JSON | 15분 | 요청 시 cache miss 인 경우 |

### 6.2 외부 API 호출 예산 (Upstash 10k cmd/day 고려)

**Cache hit 시나리오** (대부분):
- FE 3개 hook → BE 3개 엔드포인트 → Redis GET 3회/방문
- 1일 100명 × 평균 3회 방문 = 900 Redis GET → **충분**

**Cache miss 시나리오** (최악):
- overview miss: Finnhub 5 calls + Redis SET 1 = 6 ops
- news miss: Finnhub 1 call + LLM 10 calls + Redis SET 1 = 12 ops
- movers miss: Finnhub 30 calls + Redis SET 1 = 31 ops
- **1회 전체 miss = ~49 외부 호출 + 3 Redis SET**

**15분 기준 최대 miss 횟수**: 1회 (TTL > 요청 간격이므로)

**일일 최대 Redis 커맨드**:
- Cache miss: 288 (24h ÷ 5min) × 1(overview) + 96 (24h ÷ 15min) × 2(news+movers) = 480 SET
- Cache hit: 예상 ~2000 GET
- 기존 서비스 사용량 ~3000
- **합계: ~5500/10000** — 안전 마진 45%

### 6.3 Finnhub Rate Limit 준수

| 시나리오 | 분당 호출 수 | Finnhub 한도 (60/min) |
|---|---|---|
| overview miss (5 quotes) | 5/5min = 1/min | OK |
| movers miss (30 quotes) | 30/15min = 2/min | OK |
| news miss | 1/15min | OK |
| 기존 서비스 (동시) | ~10/min (최대) | OK |
| **최악 동시** | ~15/min | **여유 75%** |

---

## 7. FE Component Design

### 7.1 메인 페이지 레이아웃 (page.tsx 리팩터링)

```
┌──────────────────────────────────────────────┐
│                AI Stock Advisor               │
│          미국 주식 참고/분석 도구              │
│                                              │
│        ┌──────────────────────────┐          │
│        │       SearchBox          │          │
│        └──────────────────────────┘          │
│                                              │
│  ┌────────────────────────────────────────┐  │
│  │         MarketOverview                 │  │
│  │  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐  │  │
│  │  │S&P500│ │Nasdaq│ │ Dow  │ │ VIX  │  │  │
│  │  │5234 ↑│ │16340↓│ │39127↑│ │16.4↓ │  │  │
│  │  └──────┘ └──────┘ └──────┘ └──────┘  │  │
│  │  USD/KRW: 1,345.20 (+2.30)            │  │
│  └────────────────────────────────────────┘  │
│                                              │
│  ┌──────────────────┐ ┌──────────────────┐  │
│  │   MarketMovers   │ │   MarketNews     │  │
│  │  급등      급락  │ │                  │  │
│  │ NVDA +3.8% │INTC │ │  연준, 9월 금리  │  │
│  │ AMD  +2.1% │BA   │ │  인하 가능성...  │  │
│  │ TSLA +1.8% │PFE  │ │                  │  │
│  │ ...        │...  │ │  유가, 중동 긴장  │  │
│  │            │     │ │  완화로 하락...   │  │
│  └──────────────────┘ └──────────────────┘  │
│                                              │
│  본 서비스의 데이터와 분석은 참고용이며,      │
│  모든 투자 판단과 책임은 사용자 본인에게...    │
└──────────────────────────────────────────────┘
```

**반응형**:
- `≥1024px`: MarketMovers + MarketNews 2-column (grid-cols-2)
- `<1024px`: 1-column 스택 (MarketMovers → MarketNews 순)
- MarketOverview 지수 카드: `≥640px` 4-column, `<640px` 2-column grid

### 7.2 MarketOverview 컴포넌트

```tsx
// features/market-dashboard/market-overview.tsx
'use client';

// 지수 카드 4개 + 환율 바
// - 로딩 중: 스켈레톤 카드 (animate-pulse)
// - 에러: "시장 데이터를 불러올 수 없습니다" 메시지
// - 각 카드: 지수명, 현재가, 변동(금액+%), 상승=초록/하락=빨강
// - VIX 카드: 특별 스타일 (VIX > 20 = 경고색, > 30 = 위험색)
// - 환율: 카드 하단 1줄 바
```

**VIX 색상 기준**:
| VIX 범위 | 상태 | 색상 |
|---|---|---|
| < 20 | 안정 | green |
| 20~30 | 주의 | yellow/amber |
| > 30 | 공포 | red |

### 7.3 MarketMovers 컴포넌트

```tsx
// features/market-dashboard/market-movers.tsx
'use client';

// 급등/급락 2-column 또는 탭 (모바일은 탭)
// - 종목 행: 티커, 종목명, 현재가, 변동%, 변동색
// - 행 클릭 → router.push(`/stock/${ticker}`) 연결
// - 로딩 중: 테이블 스켈레톤
// - 에러: "급등/급락 데이터를 불러올 수 없습니다"
// - 하단: "인기 종목 {poolSize}개 기준" 안내
```

### 7.4 MarketNews 컴포넌트

```tsx
// features/market-dashboard/market-news.tsx
'use client';

// 뉴스 리스트 (최대 10건)
// - 각 아이템: 한국어 제목 (없으면 영문), 요약 1줄, 출처, 시간
// - 제목 클릭 → window.open(sourceUrl, '_blank')
// - 시간 표시: relative ("3시간 전", "1일 전")
// - 로딩 중: 뉴스 스켈레톤
// - 에러: "시장 뉴스를 불러올 수 없습니다"
```

### 7.5 React Query Hooks

```typescript
// hooks/use-market-overview.ts
export function useMarketOverview() {
  return useQuery<MarketOverview>({
    queryKey: ['market', 'overview'],
    queryFn: () => getMarketOverview(),
    staleTime: 5 * 60_000,      // 5분 (BE 캐시 TTL 과 동기화)
    refetchInterval: 5 * 60_000, // 5분마다 자동 갱신
    retry: 1,                    // 1회 재시도 후 에러 표시
  });
}

// hooks/use-market-news.ts
export function useMarketNews() {
  return useQuery<MarketNewsItem[]>({
    queryKey: ['market', 'news'],
    queryFn: () => getMarketNews(),
    staleTime: 15 * 60_000,
    refetchInterval: 15 * 60_000,
    retry: 1,
  });
}

// hooks/use-market-movers.ts
export function useMarketMovers() {
  return useQuery<MarketMovers>({
    queryKey: ['market', 'movers'],
    queryFn: () => getMarketMovers(),
    staleTime: 15 * 60_000,
    refetchInterval: 15 * 60_000,
    retry: 1,
  });
}
```

### 7.6 API 클라이언트 함수

```typescript
// lib/api/market.ts
import { apiFetch } from '@/lib/api/client';
import type { MarketOverview, MarketMovers, MarketNewsItem } from '@/types/market';

export function getMarketOverview(): Promise<MarketOverview> {
  return apiFetch<MarketOverview>('/market/overview');
}

export function getMarketNews(limit = 10): Promise<MarketNewsItem[]> {
  return apiFetch<MarketNewsItem[]>(`/market/news?limit=${limit}`);
}

export function getMarketMovers(): Promise<MarketMovers> {
  return apiFetch<MarketMovers>('/market/movers');
}
```

---

## 8. Error Handling

### 8.1 BE 부분 실패 전략

| 실패 범위 | 처리 | FE 영향 |
|---|---|---|
| 개별 지수 1개 실패 | `indices` 리스트에서 제외, 나머지 정상 반환 | 해당 카드만 미표시 |
| 환율 실패 | `usdKrw` = null | "환율 데이터 없음" 표시 |
| 모든 지수 + 환율 실패 | 503 응답 | 전체 overview 섹션 에러 |
| 뉴스 Finnhub 실패 | 빈 리스트 반환 | "뉴스 데이터 없음" |
| 뉴스 번역 실패 (개별) | `titleKo`/`summaryKo` = null | 영문 원문 표시 |
| movers quote 일부 실패 | 실패 종목 제외, 나머지로 정렬 | 정상 (풀 축소) |
| movers quote 전체 실패 | 503 응답 | movers 섹션 에러 |

### 8.2 FE 에러 UI 패턴

```
┌─────────────────────────────────────┐
│  ⚠ 시장 데이터를 불러올 수 없습니다  │
│     잠시 후 다시 시도해 주세요        │
│          [다시 시도]                  │
└─────────────────────────────────────┘
```

- 각 섹션(overview, movers, news) 독립 에러 표시
- "다시 시도" 버튼 → `queryClient.invalidateQueries(['market', section])`
- Render cold start 대비: 첫 로드 실패 시 자동 1회 재시도 (React Query `retry: 1`)

---

## 9. Implementation Order

| Step | 범위 | 상세 | 예상 파일 수 |
|---|---|---|---|
| 1 | BE: 도메인 + 기반 | `market` 패키지, DTOs, `PopularTickerPool`, `FinnhubMarketNewsClient` | 7~8 |
| 2 | BE: `/market/overview` | `MarketOverviewService` + Controller 메서드 + Redis 캐시 | 2~3 |
| 3 | BE: `/market/news` | `MarketNewsService` + Controller 메서드 + 번역 연동 | 2~3 |
| 4 | BE: `/market/movers` | `MarketMoversService` + Controller 메서드 + 병렬 quote | 2~3 |
| 5 | FE: 타입 + API 함수 | `types/market.ts`, `lib/api/market.ts` | 2 |
| 6 | FE: 위젯 컴포넌트 | `market-overview.tsx`, `market-news.tsx`, `market-movers.tsx` + hooks | 6 |
| 7 | FE: 메인 페이지 통합 | `page.tsx` 리팩터링 (검색 + 대시보드) | 1 |
| 8 | 통합 검증 | BE 단위 테스트, FE 동작 확인, Render/Vercel 배포 | 2~3 |

---

## 10. Dependencies

| 항목 | 상태 | 재사용 방식 |
|---|---|---|
| `FinnhubClient` | 구현 완료 | `.quote(symbol)` 직접 호출 (지수 + movers) |
| `FinnhubNewsClient` | 구현 완료 | 참고만 — 신규 `FinnhubMarketNewsClient` 생성 (`/news?category=general`) |
| `NewsTranslator` | 구현 완료 | `.translate(CompanyNews)` 직접 호출 (시장 뉴스 번역) |
| `RedisCacheAdapter` | 구현 완료 | `.getOrLoad(key, type, ttl, loader)` 패턴 |
| `StockProfileService` | 구현 완료 | movers 종목명 조회 시 기존 캐시 활용 |
| `TwelveDataClient` | 구현 완료 | 지수 quote fallback |
| `Disclaimers` | 구현 완료 | 면책 문구 상수 추가 필요 (`Disclaimers.MARKET`, `Disclaimers.MARKET_NEWS`) |
| `apiFetch` (FE) | 구현 완료 | market API 함수에서 래핑 |

---

## 11. Non-Functional Requirements

| 항목 | 목표 | 측정 방법 |
|---|---|---|
| 대시보드 로딩 (cache hit) | < 500ms | BE 응답 시간 |
| 대시보드 로딩 (cache miss) | < 3s | BE 응답 시간 (외부 API 포함) |
| 모바일 반응형 | 카드 1열 스택, 스크롤 없음 | 375px 뷰포트 테스트 |
| 면책 고지 | 대시보드 하단 + 각 섹션 데이터에 포함 | UI 확인 |
| 부분 실패 | 1개 섹션 에러 시 나머지 정상 | 의도적 API 차단 테스트 |
| Upstash 일일 한도 | 기존+신규 합산 < 8000 cmd/day | Upstash 대시보드 모니터링 |

---

## 12. Risks & Mitigations

| 리스크 | 영향 | 대응 |
|---|---|---|
| Finnhub 지수 심볼 미지원 | overview 에 지수 데이터 없음 | Twelve Data fallback (API 키 보유) |
| VIX 조회 불가 | VIX 카드 비어있음 | VIX 카드 조건부 렌더링, 미지원 시 숨김 |
| 30종목 일괄 quote 시 rate limit | movers 데이터 불완전 | 15분 캐시 + parallelStream 으로 burst 분산, 실패 종목 skip |
| 시장 뉴스 번역 LLM 비용 | Gemini 호출 10건/15분 | 캐시 TTL 15분으로 하루 ~960건 최대. Gemini Flash 단가 낮음 |
| Render cold start + 대시보드 | 첫 로딩 1~2분 | FE 스켈레톤 UI + "서버 준비 중" 안내 + React Query retry |
| USD/KRW 환율 정확도 | Finnhub forex rates 지연 | 15분 캐시이므로 대시보드 용도로 충분 |
