# Design: stock-detail-enrichment

> Plan: `docs/01-plan/features/stock-detail-enrichment.plan.md`
> Feature: 종목 상세 페이지 기업 펀더멘털 정보 강화

---

## 1. Architecture Overview

```
┌──────────────────────────────────────────────────────────┐
│ FE: CompanyOverviewPanel                                 │
│   useCompanyOverview(ticker)   useQuote(ticker)          │
│         ↓                          ↓                     │
│   GET /stocks/{t}/overview    GET /stocks/{t}/quote      │
└──────────┬───────────────────────────┬───────────────────┘
           │                           │
┌──────────▼───────────────────────────▼───────────────────┐
│ BE: StockController                                      │
│   → CompanyOverviewService    → QuoteService             │
│       ↓ cache miss                                       │
│   FmpClient.companyProfile()  YahooFinance.quote()       │
│       ↓                          ↓ (기존 + week52)       │
│   Redis 24h TTL               Redis 30s TTL              │
└──────────────────────────────────────────────────────────┘
           │                           │
┌──────────▼───────────────────────────▼───────────────────┐
│ External APIs                                            │
│   FMP: GET /profile/{symbol}?apikey=...                  │
│   Yahoo: GET /v8/finance/chart/{symbol}?interval=1m&...  │
└──────────────────────────────────────────────────────────┘
```

---

## 2. Data Flow

### 2.1 CompanyOverview 조회 흐름

```
Client → GET /stocks/AAPL/overview
  → CompanyOverviewService.getOverview("AAPL")
    → RedisCacheAdapter.getOrLoad("overview:AAPL", ..., 24h, loader)
      → [캐시 히트] 즉시 반환
      → [캐시 미스] FmpClient.companyProfile("AAPL")
        → GET https://financialmodelingprep.com/api/v3/profile/AAPL?apikey=xxx
        → FmpProfile → CompanyOverview 변환
        → Redis SET (24h TTL)
        → 반환
```

### 2.2 Quote 52주 고저 추출 (기존 확장)

```
YahooFinanceClient.quote("AAPL")
  → meta 노드에서 기존 필드 추출 + 추가:
    - fiftyTwoWeekHigh → week52High
    - fiftyTwoWeekLow  → week52Low
  → Quote record에 포함 (기존 캐시 30s에 함께 저장)
```

### 2.3 StockDetail aggregate 확장

```
StockDetailService.getDetail(ticker, tf)
  → 기존 6 블록 병렬 호출:
    profileF, quoteF, candlesF, indF, newsF, aiF
  → 추가 1 블록:
    overviewF = executor.submit(() → overviewService.getOverview(ticker))
  → await("overview", overviewF, errors)
  → StockDetailResponse에 overview 필드 포함 (null 허용)
```

---

## 3. Domain Model

### 3.1 CompanyOverview (신규)

**파일**: `apps/api/src/main/java/com/aistockadvisor/stock/domain/CompanyOverview.java`

```java
public record CompanyOverview(
    String sector,           // "Technology"
    String industry,         // "Consumer Electronics"
    BigDecimal marketCap,    // 3400000000000
    BigDecimal peRatio,      // 28.5 (nullable)
    BigDecimal eps,           // 6.73 (nullable)
    BigDecimal dividendPerShare, // 1.00 (nullable)
    BigDecimal beta,          // 1.24 (nullable)
    BigDecimal week52High,   // 260.10
    BigDecimal week52Low,    // 164.08
    String description,      // 영문 기업 설명 (nullable)
    Integer employees,       // 164000 (nullable)
    String website,          // "https://apple.com" (nullable)
    String ipoDate           // "1980-12-12" (nullable)
) {}
```

### 3.2 Quote 확장

**파일**: `apps/api/src/main/java/com/aistockadvisor/stock/domain/Quote.java`

기존 12 필드 + 2 필드 추가:

```java
public record Quote(
    String ticker,
    BigDecimal price,
    BigDecimal change,
    BigDecimal changePercent,
    BigDecimal high,
    BigDecimal low,
    BigDecimal open,
    BigDecimal previousClose,
    long volume,
    OffsetDateTime updatedAt,
    MarketStatus marketStatus,
    String priceLabel,
    BigDecimal week52High,    // 추가
    BigDecimal week52Low      // 추가
) {}
```

### 3.3 StockDetailResponse 확장

```java
public record StockDetailResponse(
    StockProfile profile,
    Quote quote,
    List<Candle> candles,
    IndicatorSnapshot indicators,
    List<NewsItem> news,
    AiSignal aiSignal,
    CompanyOverview overview,   // 추가 (nullable)
    Disclaimer disclaimer,
    boolean partial,
    List<BlockError> errors,
    Meta meta
) {}
```

---

## 4. Implementation Steps

### Step 1: CompanyOverview 도메인 레코드

**파일**: `stock/domain/CompanyOverview.java` (신규)

- 13 필드 record
- 모든 숫자 필드 nullable (FMP 응답에 null 가능)
- Jackson 직렬화 호환 (record 기본 동작)

### Step 2: Quote record 확장

**파일**: `stock/domain/Quote.java`

- `week52High` (BigDecimal, nullable) 추가
- `week52Low` (BigDecimal, nullable) 추가
- **영향 범위**: Quote를 생성하는 모든 클라이언트 수정 필요
  - `YahooFinanceClient.quote()` — meta에서 추출
  - `FinnhubClient.quote()` — null 전달
  - `TwelveDataClient.quote()` — null 전달
  - `QuoteService` — 변경 없음 (fallback 체인이 Quote를 그대로 반환)

### Step 3: FmpClient 확장

**파일**: `market/infra/FmpClient.java`

```java
// 신규 inner record
@JsonIgnoreProperties(ignoreUnknown = true)
public record FmpProfile(
    String symbol,
    String companyName,
    String sector,
    String industry,
    Long mktCap,
    Double beta,
    Double lastDiv,
    String description,
    String ceo,
    Integer fullTimeEmployees,
    String website,
    String ipoDate
) {}

// 신규 메서드
public FmpProfile companyProfile(String ticker) {
    try {
        FmpProfile[] resp = webClient.get()
            .uri(b -> b.path("/api/v3/profile/{symbol}")
                .queryParam("apikey", apiKey)
                .build(ticker))
            .retrieve()
            .bodyToMono(FmpProfile[].class)
            .block(TIMEOUT);
        return (resp != null && resp.length > 0) ? resp[0] : null;
    } catch (WebClientResponseException ex) {
        // 기존 fetchMovers와 동일한 에러 처리
        ...
    }
}
```

> FMP `/profile/{symbol}` 응답은 배열(`[{...}]`)이므로 `FmpProfile[]`로 디시리얼라이즈 후 `[0]` 추출.

### Step 4: YahooFinanceClient.quote() 확장

**파일**: `stock/infra/client/YahooFinanceClient.java`

`quote()` 메서드의 meta 파싱 부분에서 추가 추출:

```java
BigDecimal week52High = tobd(meta.path("fiftyTwoWeekHigh"));
BigDecimal week52Low = tobd(meta.path("fiftyTwoWeekLow"));

return new Quote(
    ticker, price, change, changePct,
    tobd(meta.path("regularMarketDayHigh")),
    tobd(meta.path("regularMarketDayLow")),
    tobd(meta.path("regularMarketOpen")),
    prevClose,
    meta.path("regularMarketVolume").asLong(0),
    updatedAt, status,
    MarketStatusResolver.priceLabel(status, updatedAt),
    week52High,  // 추가
    week52Low    // 추가
);
```

### Step 5: FinnhubClient / TwelveDataClient Quote 생성자 호환

두 클라이언트의 `quote()` 메서드에서 Quote 생성 시 week52High/Low에 `null` 전달.

**FinnhubClient.quote()**: 기존 생성자 끝에 `, null, null` 추가
**TwelveDataClient.quote()**: 기존 생성자 끝에 `, null, null` 추가

### Step 6: CompanyOverviewService

**파일**: `stock/service/CompanyOverviewService.java` (신규)

```java
@Service
public class CompanyOverviewService {

    private static final Duration CACHE_TTL = Duration.ofHours(24);
    private static final TypeReference<CompanyOverview> TYPE = new TypeReference<>() {};

    private final FmpClient fmpClient;
    private final RedisCacheAdapter cache;

    public CompanyOverviewService(FmpClient fmpClient, RedisCacheAdapter cache) {
        this.fmpClient = fmpClient;
        this.cache = cache;
    }

    public CompanyOverview getOverview(String ticker) {
        return cache.getOrLoad("overview:" + ticker, TYPE, CACHE_TTL,
            () -> fetchFromFmp(ticker));
    }

    private CompanyOverview fetchFromFmp(String ticker) {
        FmpClient.FmpProfile p = fmpClient.companyProfile(ticker);
        if (p == null) return null;
        return new CompanyOverview(
            p.sector(),
            p.industry(),
            p.mktCap() != null ? BigDecimal.valueOf(p.mktCap()) : null,
            null,  // peRatio — FMP profile에 없음, FE에서 계산 가능
            null,  // eps — FMP profile에 없음
            p.lastDiv() != null ? BigDecimal.valueOf(p.lastDiv()) : null,
            p.beta() != null ? BigDecimal.valueOf(p.beta()) : null,
            null,  // week52High — Quote에서 제공
            null,  // week52Low — Quote에서 제공
            p.description(),
            p.fullTimeEmployees(),
            p.website(),
            p.ipoDate()
        );
    }
}
```

> `peRatio`, `eps`, `week52High/Low`는 CompanyOverview가 아닌 Quote에서 제공. FE에서 두 소스를 합성하여 표시.

### Step 7: StockController 엔드포인트

**파일**: `stock/web/StockController.java`

```java
// 신규 주입
private final CompanyOverviewService overviewService;

// 생성자에 overviewService 추가

@GetMapping("/{ticker}/overview")
public CompanyOverview overview(
    @PathVariable("ticker") @Pattern(regexp = TICKER_REGEX) String ticker) {
    CompanyOverview ov = overviewService.getOverview(ticker);
    if (ov == null) {
        throw new BusinessException(ErrorCode.TICKER_NOT_FOUND);
    }
    return ov;
}
```

### Step 8: StockDetailResponse + StockDetailService 확장

**StockDetailResponse**: `overview` (CompanyOverview, nullable) 필드 추가 — `aiSignal` 뒤, `disclaimer` 앞.

**StockDetailService**: `getDetail()` 내 virtual thread 블록에 overview 추가:

```java
Future<CompanyOverview> overviewF = executor.submit(
    () -> overviewService.getOverview(ticker));
// ...
CompanyOverview overview = await("overview", overviewF, errors);
// ...
return new StockDetailResponse(
    profile, quote, candles, indicators, news, aiSignal,
    overview,  // 추가
    disclaimer, !errors.isEmpty(), ...
);
```

### Step 9: FE 타입 확장

**파일**: `apps/web/src/types/stock.ts`

```typescript
export interface CompanyOverview {
  sector: string | null;
  industry: string | null;
  marketCap: number | null;
  peRatio: number | null;
  eps: number | null;
  dividendPerShare: number | null;
  beta: number | null;
  week52High: number | null;
  week52Low: number | null;
  description: string | null;
  employees: number | null;
  website: string | null;
  ipoDate: string | null;
}

// Quote에 추가
interface Quote {
  // ... 기존 필드
  week52High: number | null;
  week52Low: number | null;
}

// StockDetail에 추가
interface StockDetail {
  // ... 기존 필드
  overview: CompanyOverview | null;
}
```

### Step 10: 포맷 유틸 확장

**파일**: `apps/web/src/lib/format/number.ts`

기존 `formatCompact` 외 추가:

```typescript
export function formatMarketCap(value: number | null | undefined): string {
  if (value == null || !Number.isFinite(value)) return '—';
  if (value >= 1e12) return `$${(value / 1e12).toFixed(1)}T`;
  if (value >= 1e9) return `$${(value / 1e9).toFixed(1)}B`;
  if (value >= 1e6) return `$${(value / 1e6).toFixed(1)}M`;
  return `$${compact.format(value)}`;
}

export function formatRatio(value: number | null | undefined, suffix = ''): string {
  if (value == null || !Number.isFinite(value)) return '—';
  return `${value.toFixed(2)}${suffix}`;
}

export function formatEmployees(value: number | null | undefined): string {
  if (value == null) return '—';
  return value.toLocaleString('en-US');
}
```

### Step 11: FE API 함수 + React Query 훅

**파일**: `apps/web/src/lib/api/stocks.ts`

```typescript
export function getCompanyOverview(ticker: string): Promise<CompanyOverview> {
  return apiFetch<CompanyOverview>(`/stocks/${ticker}/overview`);
}
```

**파일**: `apps/web/src/features/stock-detail/hooks/use-company-overview.ts` (신규)

```typescript
'use client';
import { useQuery } from '@tanstack/react-query';
import { getCompanyOverview } from '@/lib/api/stocks';
import type { CompanyOverview } from '@/types/stock';

export function useCompanyOverview(ticker: string) {
  return useQuery<CompanyOverview>({
    queryKey: ['overview', ticker],
    queryFn: () => getCompanyOverview(ticker),
    staleTime: 24 * 60 * 60 * 1000, // 24h
    retry: false, // FMP 실패 시 재시도 안 함 (graceful degradation)
  });
}
```

### Step 12: CompanyOverviewPanel 컴포넌트

**파일**: `apps/web/src/features/stock-detail/components/company-overview-panel.tsx` (신규)

**구조**:

```
<section>
  <h2>기업 개요</h2>
  
  <!-- 지표 그리드: 2x3 (데스크탑) / 1열 (모바일) -->
  <div class="grid grid-cols-2 sm:grid-cols-3 gap-4">
    <StatItem label="섹터" value={overview.sector} />
    <StatItem label="시가총액" value={formatMarketCap(overview.marketCap)} />
    <StatItem label="P/E" value={formatRatio(quote.week52High ? ... )} />
    <StatItem label="EPS" value={formatRatio(overview.eps, '')} prefix="$" />
    <StatItem label="배당" value={formatRatio(overview.dividendPerShare, '')} prefix="$" />
    <StatItem label="베타" value={formatRatio(overview.beta)} />
  </div>
  
  <!-- 52주 범위 바 -->
  <Week52RangeBar
    low={quote.week52Low}
    high={quote.week52High}
    current={quote.price}
  />
  
  <!-- 기업 설명 (접이식) -->
  <CollapsibleDescription text={overview.description} maxLines={3} />
</section>
```

**StatItem 로컬 컴포넌트**:

```typescript
function StatItem({ label, value, prefix }: {
  label: string; value: string; prefix?: string;
}) {
  return (
    <div>
      <dt className="text-xs text-fg-muted">{label}</dt>
      <dd className="text-sm font-semibold text-fg">
        {prefix}{value}
      </dd>
    </div>
  );
}
```

**Week52RangeBar 로컬 컴포넌트**:

```typescript
function Week52RangeBar({ low, high, current }: {
  low: number | null; high: number | null; current: number | null;
}) {
  if (low == null || high == null || current == null) return null;
  const range = high - low;
  const pct = range > 0 ? ((current - low) / range) * 100 : 50;
  const clampedPct = Math.max(0, Math.min(100, pct));
  
  return (
    <div>
      <div className="flex items-center justify-between text-xs text-fg-muted">
        <span>{formatUsd(low)}</span>
        <span className="text-xs font-medium text-fg-secondary">52주 범위</span>
        <span>{formatUsd(high)}</span>
      </div>
      <div className="relative mt-1 h-2 rounded-full bg-bg-muted">
        <div className="absolute left-0 top-0 h-full rounded-full bg-brand"
             style={{ width: `${clampedPct}%` }} />
        <div className="absolute top-1/2 -translate-x-1/2 -translate-y-1/2
                        h-3.5 w-3.5 rounded-full border-2 border-bg bg-brand"
             style={{ left: `${clampedPct}%` }} />
      </div>
    </div>
  );
}
```

**CollapsibleDescription**: 기존 `collapsible-section.tsx` 패턴 재사용 또는 간단한 `line-clamp-3` + "더 보기" 토글.

### Step 13: StockDetailView에 CompanyOverviewPanel 배치

**파일**: `apps/web/src/features/stock-detail/stock-detail-view.tsx`

```typescript
import { CompanyOverviewPanel } from '@/features/stock-detail/components/company-overview-panel';

export function StockDetailView({ ticker }: { ticker: string }) {
  const [tf, setTf] = useState<TimeFrame>('1D');

  return (
    <div className="flex flex-col gap-6">
      <StockHeader ticker={ticker} />
      <CompanyOverviewPanel ticker={ticker} />   {/* 신규 */}
      <TimeFrameTabs value={tf} onChange={setTf} />
      <ChartPanel ticker={ticker} tf={tf} />
      <IndicatorsPanel ticker={ticker} />
      <AiSignalPanel ticker={ticker} tf={tf} />
      <NewsPanel ticker={ticker} />
    </div>
  );
}
```

---

## 5. Cache Strategy

| 키 패턴 | TTL | 용도 |
|---|---|---|
| `overview:{ticker}` | 24시간 | FMP Company Profile |
| `quote:{ticker}` | 30초 | Yahoo/Finnhub 시세 (week52 포함) |
| `profile:{ticker}` | 24시간 | Finnhub 기본 프로필 (변경 없음) |

FMP 250 req/day 예산:
- 24h 캐시 → 하루 최대 250 고유 종목 조회 가능
- 동일 종목 재방문 = Redis hit (FMP 호출 0)
- 베타 DAU 기준 충분

---

## 6. Error Handling

| 실패 지점 | 처리 |
|---|---|
| FMP API 429 (rate limit) | `BusinessException(UPSTREAM_RATE_LIMIT)` → overview=null, 나머지 정상 |
| FMP API 5xx/timeout | `BusinessException(UPSTREAM_TIMEOUT)` → overview=null |
| FMP 응답에 ticker 없음 | `companyProfile()` returns null → overview=null |
| Yahoo meta에 week52 없음 | `tobd()` returns null → Quote.week52High/Low = null |
| StockDetail aggregate 내 overview 실패 | `await("overview", ...)` → null + errors에 기록 + partial=true |
| FE overview API 실패 | `useCompanyOverview` retry=false → 패널 미렌더링 (graceful) |

---

## 7. Affected Files Summary

### 신규 파일 (4)

| # | 파일 | 설명 |
|---|---|---|
| 1 | `stock/domain/CompanyOverview.java` | 도메인 record (13 fields) |
| 2 | `stock/service/CompanyOverviewService.java` | FMP → 캐시 서비스 |
| 3 | `features/stock-detail/hooks/use-company-overview.ts` | React Query 훅 |
| 4 | `features/stock-detail/components/company-overview-panel.tsx` | UI 컴포넌트 |

### 수정 파일 (10)

| # | 파일 | 변경 내용 |
|---|---|---|
| 5 | `stock/domain/Quote.java` | +week52High, +week52Low (2 fields) |
| 6 | `stock/domain/StockDetailResponse.java` | +overview 필드 |
| 7 | `market/infra/FmpClient.java` | +FmpProfile record, +companyProfile() |
| 8 | `stock/infra/client/YahooFinanceClient.java` | quote() meta 52주 추출 |
| 9 | `stock/infra/client/FinnhubClient.java` | Quote 생성자 호환 (+null, null) |
| 10 | `stock/infra/client/TwelveDataClient.java` | Quote 생성자 호환 (+null, null) |
| 11 | `stock/web/StockController.java` | +GET /overview 엔드포인트 |
| 12 | `stock/service/StockDetailService.java` | overview 블록 추가 |
| 13 | `types/stock.ts` | +CompanyOverview, Quote 확장, StockDetail 확장 |
| 14 | `lib/format/number.ts` | +formatMarketCap, +formatRatio, +formatEmployees |
| 15 | `lib/api/stocks.ts` | +getCompanyOverview() |
| 16 | `stock-detail-view.tsx` | CompanyOverviewPanel 배치 |

### 변경 없는 파일

- `FinnhubClient.quote()` 외 다른 메서드 — 변경 없음
- `CandleService`, `NewsService`, `AiSignalService` — 영향 없음
- `StockProfileService` — FMP로 대체하지 않음 (Finnhub profile 유지)

---

## 8. Implementation Order

```
Step 1: CompanyOverview.java (도메인)
Step 2: Quote.java 확장 (week52 필드)
  ↓
Step 3: FmpClient.companyProfile() (infra)
Step 4: YahooFinanceClient.quote() 52주 추출
Step 5: FinnhubClient + TwelveDataClient Quote 호환
  ↓
Step 6: CompanyOverviewService (서비스)
Step 7: StockController + overview 엔드포인트
Step 8: StockDetailResponse + StockDetailService
  ↓
Step 9: FE types/stock.ts 확장
Step 10: lib/format/number.ts 유틸
Step 11: lib/api/stocks.ts + use-company-overview.ts
  ↓
Step 12: company-overview-panel.tsx (UI)
Step 13: stock-detail-view.tsx 배치
```

BE 먼저 완성 → FE 타입 → FE UI 순서. 각 Step은 독립 빌드 가능.

---

## 9. Testing Checklist

| # | 테스트 | 방법 |
|---|---|---|
| T-01 | FmpClient.companyProfile("AAPL") 정상 반환 | dev 서버 + 수동 호출 |
| T-02 | YahooFinanceClient.quote("AAPL") week52High/Low 포함 | 기존 테스트에 assertion 추가 |
| T-03 | CompanyOverviewService Redis 캐시 동작 | 2회 호출 → 1회만 FMP 호출 확인 (로그) |
| T-04 | GET /stocks/AAPL/overview 200 응답 | curl 또는 브라우저 |
| T-05 | GET /stocks/AAPL/detail overview 필드 포함 | curl 확인 |
| T-06 | FMP 실패 시 overview=null, 나머지 정상 | API key 제거 후 테스트 |
| T-07 | FE CompanyOverviewPanel 렌더링 | dev 서버 브라우저 확인 |
| T-08 | 52주 바 위치 정확성 | AAPL 현재가 vs 52주 범위 비율 검증 |
| T-09 | 모바일 반응형 | 375px 뷰포트 확인 |
| T-10 | `make web-check` + `make api-check` 통과 | CI 등가 |
