# Design: dashboard-expansion

> **Plan Reference**: `docs/01-plan/features/dashboard-expansion.plan.md`
> **Created**: 2026-04-23
> **Branch**: feat/dashboard-expansion

---

## 1. Overview

메인 대시보드를 "주요 지수" / "섹터 퍼포먼스" / "환율·금리·원자재" 3개 섹션으로 재구성하고,
GICS 11개 섹터 변동률 + 10Y 국채 금리 + 금·WTI 원자재 카드를 추가합니다.

---

## 2. Architecture

### 2.1 BE 변경 범위

```
market/
├── domain/
│   ├── MarketIndex.java               (기존 — 변경 없음)
│   ├── MarketOverviewResponse.java    (확장 — macro 필드 추가)
│   └── SectorPerformance.java         (신규)
├── infra/
│   └── FmpClient.java                 (확장 — sectorPerformance() 추가)
├── service/
│   ├── MarketOverviewService.java     (확장 — 금리·원자재 조회 추가)
│   └── SectorPerformanceService.java  (신규)
└── web/
    └── MarketController.java          (확장 — sectors() 엔드포인트 추가)
```

### 2.2 FE 변경 범위

```
features/market-dashboard/
├── market-dashboard.tsx               (수정 — 레이아웃 재구성)
├── market-overview.tsx                (수정 — 지수/매크로 섹션 분리)
├── sector-performance.tsx             (신규)
├── market-movers.tsx                  (변경 없음)
├── market-news.tsx                    (변경 없음)
└── hooks/
    ├── use-market-overview.ts         (변경 없음)
    └── use-sector-performance.ts      (신규)

types/
└── market.ts                          (확장 — SectorPerformance, MacroItem 추가)

lib/api/
└── market.ts                          (확장 — getSectorPerformance() 추가)
```

### 2.3 데이터 흐름

```
[FMP /sector-performance] ──→ SectorPerformanceService ──→ Redis (15min)
                                                             ↓
[Finnhub/Yahoo/TwelveData] ──→ MarketOverviewService ──→ Redis (5min)
     (^TNX, GC=F, CL=F)            (기존 지수 + 환율 + macro 추가)
                                                             ↓
                                     MarketController ──→ REST API
                                                             ↓
                                  React Query hooks ──→ FE Components
```

---

## 3. BE Design

### 3.1 SectorPerformance record (신규)

```java
package com.aistockadvisor.market.domain;

public record SectorPerformance(
    String sector,           // "Technology", "Healthcare" 등
    String sectorKo,         // "기술", "헬스케어" 등
    Double changePercent     // 일간 변동률 (%)
) {}
```

### 3.2 MarketOverviewResponse 확장

```java
public record MarketOverviewResponse(
    List<MarketIndex> indices,
    BigDecimal usdKrw,
    BigDecimal usdKrwChange,
    List<MarketIndex> macro,       // 신규: 10Y Treasury, Gold, WTI
    OffsetDateTime updatedAt,
    String disclaimer
) {}
```

- `macro` 필드에 금리·원자재를 `MarketIndex` 재활용하여 담음
- 기존 `indices`(지수)와 분리하되, 동일 DTO 구조 재활용 (symbol, name, price, change, changePercent)
- `usdKrw`/`usdKrwChange`는 기존 위치 유지 (하위 호환)

### 3.3 FmpClient.sectorPerformance() (신규 메서드)

```java
public List<FmpSectorPerformance> sectorPerformance() {
    FmpSectorPerformance[] resp = webClient.get()
        .uri(b -> b.path("/sector-performance")
            .queryParam("apikey", apiKey)
            .build())
        .retrieve()
        .bodyToMono(FmpSectorPerformance[].class)
        .block(TIMEOUT);
    return resp == null ? List.of() : List.of(resp);
}

@JsonIgnoreProperties(ignoreUnknown = true)
public record FmpSectorPerformance(
    String sector,
    @JsonProperty("changesPercentage") Double changePercent
) {}
```

### 3.4 SectorPerformanceService (신규)

```java
@Service
public class SectorPerformanceService {

    private static final Duration TTL = Duration.ofMinutes(15);

    private final FmpClient fmpClient;
    private final YahooFinanceClient yahooClient;
    private final RedisCacheAdapter cache;

    // 섹터 → 한국어명 매핑
    private static final Map<String, String> SECTOR_KO = Map.ofEntries(
        Map.entry("Technology", "기술"),
        Map.entry("Healthcare", "헬스케어"),
        Map.entry("Financial Services", "금융"),
        Map.entry("Consumer Cyclical", "임의소비재"),
        Map.entry("Communication Services", "커뮤니케이션"),
        Map.entry("Industrials", "산업재"),
        Map.entry("Consumer Defensive", "필수소비재"),
        Map.entry("Energy", "에너지"),
        Map.entry("Utilities", "유틸리티"),
        Map.entry("Real Estate", "부동산"),
        Map.entry("Basic Materials", "소재")
    );

    // Yahoo fallback용 섹터 ETF 매핑
    private static final String[][] SECTOR_ETFS = {
        {"XLK", "Technology", "기술"},
        {"XLV", "Healthcare", "헬스케어"},
        {"XLF", "Financial Services", "금융"},
        {"XLY", "Consumer Cyclical", "임의소비재"},
        {"XLC", "Communication Services", "커뮤니케이션"},
        {"XLI", "Industrials", "산업재"},
        {"XLP", "Consumer Defensive", "필수소비재"},
        {"XLE", "Energy", "에너지"},
        {"XLU", "Utilities", "유틸리티"},
        {"XLRE", "Real Estate", "부동산"},
        {"XLB", "Basic Materials", "소재"},
    };

    public List<SectorPerformance> getSectors() {
        return cache.getOrLoad("market:sectors", TYPE, TTL,
            this::fetchWithFallback);
    }

    private List<SectorPerformance> fetchWithFallback() {
        List<SectorPerformance> sectors = fetchFromFmp();
        if (!sectors.isEmpty()) return sectors;

        log.debug("FMP sector-performance fallback to Yahoo ETFs");
        return fetchFromYahooEtfs();
    }

    private List<SectorPerformance> fetchFromFmp() {
        // FmpClient.sectorPerformance() 호출
        // FmpSectorPerformance → SectorPerformance 변환 (한국어명 매핑)
        // changePercent 기준 내림차순 정렬
    }

    private List<SectorPerformance> fetchFromYahooEtfs() {
        // SECTOR_ETFS 11종을 parallel stream으로 Yahoo quote 호출
        // changePercent 추출 → SectorPerformance 생성
        // 실패한 종목 skip (graceful degradation)
    }
}
```

### 3.5 MarketOverviewService 확장

기존 `fetchOverview()` 에 금리·원자재 조회를 추가합니다.

```java
// 신규 매크로 심볼 매핑 (기존 INDEX_SYMBOLS 패턴과 동일)
private static final String[][] MACRO_SYMBOLS = {
    {"^TNX", "^TNX", "TNX", "10Y Treasury"},
    {"GC=F", "GC=F", "XAU/USD", "Gold"},
    {"CL=F", "CL=F", "WTI/USD", "WTI Oil"},
};

private MarketOverviewResponse fetchOverview() {
    List<MarketIndex> indices = fetchIndices();       // 기존
    BigDecimal[] forex = fetchUsdKrw();              // 기존
    List<MarketIndex> macro = fetchMacro();           // 신규

    // ... 기존 로직 유지
    return new MarketOverviewResponse(
        indices, forex[0], forex[1], macro, now, disclaimer
    );
}

private List<MarketIndex> fetchMacro() {
    return java.util.Arrays.stream(MACRO_SYMBOLS)
        .parallel()
        .map(sym -> fetchIndex(sym[0], sym[1], sym[2], sym[3]))
        .filter(Objects::nonNull)
        .toList();
}
```

- `fetchIndex()` 메서드를 기존 지수와 동일하게 재활용 (3-tier fallback)
- Finnhub이 선물 심볼(GC=F, CL=F)을 미지원할 경우 자동으로 Yahoo → TwelveData fallback

### 3.6 MarketController.sectors() (신규 엔드포인트)

```java
@GetMapping("/sectors")
public List<SectorPerformance> sectors() {
    return sectorPerformanceService.getSectors();
}
```

---

## 4. FE Design

### 4.1 타입 확장 (`types/market.ts`)

```typescript
// 기존 MarketOverview 확장
export interface MarketOverview {
  indices: MarketIndex[];
  usdKrw: number | null;
  usdKrwChange: number | null;
  macro: MarketIndex[];              // 신규
  updatedAt: string;
  disclaimer: string;
}

// 신규
export interface SectorPerformance {
  sector: string;
  sectorKo: string;
  changePercent: number;
}
```

### 4.2 API 함수 (`lib/api/market.ts`)

```typescript
// 신규
export function getSectorPerformance(): Promise<SectorPerformance[]> {
  return apiFetch<SectorPerformance[]>('/market/sectors');
}
```

### 4.3 useSectorPerformance 훅 (신규)

```typescript
// features/market-dashboard/hooks/use-sector-performance.ts
export function useSectorPerformance() {
  return useQuery({
    queryKey: ['market', 'sectors'],
    queryFn: getSectorPerformance,
    staleTime: 15 * 60 * 1000,       // 15분
    refetchInterval: 15 * 60 * 1000,  // 15분
    retry: 1,
  });
}
```

### 4.4 MarketOverview 리팩터

기존 단일 그리드를 2개 섹션으로 분리합니다.

```
MarketOverview (기존 파일 수정)
├── <SectionLabel>주요 지수</SectionLabel>
│   └── grid: IndexCard × 4 (S&P500, Nasdaq, Dow, VIX)
│
├── (섹터 위젯은 별도 컴포넌트 — MarketDashboard에서 배치)
│
└── <SectionLabel>환율 · 금리 · 원자재</SectionLabel>
    └── grid: UsdKrwCard + MacroCard × 3 (10Y, Gold, WTI)
```

**SectionLabel 컴포넌트**: 섹션 제목 라벨 (`text-xs font-medium text-fg-muted uppercase tracking-wide mb-2`)

**MacroCard 컴포넌트**: IndexCard 재활용, 단위 표시만 차별화
- 10Y Treasury: `{price}%` (금리는 % 단위)
- Gold / WTI: `${price}` (USD 표시)

### 4.5 SectorPerformance 위젯 (신규 컴포넌트)

```
sector-performance.tsx

<section aria-label="섹터 퍼포먼스">
  <SectionLabel>섹터 퍼포먼스</SectionLabel>
  <div className="flex gap-2 overflow-x-auto pb-2 scrollbar-thin">
    {sectors
      .sort((a, b) => b.changePercent - a.changePercent)
      .map(s => <SectorChip key={s.sector} sector={s} />)
    }
  </div>
</section>
```

**SectorChip 컴포넌트**:
```
┌──────────────┐
│  기술         │
│  +1.24%      │
└──────────────┘
```

- 양수: `bg-emerald-500/10 text-success` / 음수: `bg-red-500/10 text-danger`
- `min-w-[80px]` 고정 너비, `flex-shrink-0`
- 변동률 기준 내림차순 정렬 (가장 강한 섹터가 좌측)
- 모바일: 가로 스크롤 (`overflow-x-auto`)
- 데스크탑: `flex-wrap` 으로 전체 표시

### 4.6 MarketDashboard 레이아웃 재구성

```typescript
export function MarketDashboard() {
  return (
    <div className="flex flex-col gap-6">
      <MarketOverview />              {/* 지수 + 매크로 (2섹션) */}
      <SectorPerformance />           {/* 섹터 (독립 React Query) */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <MarketMovers />              {/* 급등락 (기존) */}
        <MarketNews />                {/* 뉴스 (기존) */}
      </div>
    </div>
  );
}
```

**배치 순서**: 지수 → 매크로 → 섹터 → 급등락 + 뉴스

> Plan에서는 "지수 → 섹터 → 매크로 → 급등락+뉴스" 순서였으나,
> MarketOverview 컴포넌트가 지수 + 매크로를 한 API에서 받으므로
> 지수와 매크로를 연속 배치하는 것이 데이터 흐름상 자연스럽습니다.
> 섹터는 별도 API이므로 그 아래에 배치합니다.

### 4.7 로딩/에러 상태

| 컴포넌트 | 로딩 | 에러 | Graceful Degradation |
|---------|------|------|---------------------|
| MarketOverview (지수) | skeleton 4칸 | 에러 메시지 + 재시도 | 일부 지수 없으면 있는 것만 표시 |
| MarketOverview (매크로) | skeleton 4칸 | — | macro 배열 빈 경우 섹션 숨김 |
| SectorPerformance | skeleton 바 | 에러 시 전체 숨김 | FMP 실패 → Yahoo fallback |

---

## 5. API Specification

### 5.1 GET /api/v1/market/overview (기존 확장)

**Response 변경**:
```json
{
  "indices": [
    { "symbol": "^GSPC", "name": "S&P 500", "price": 5321.45, "change": 42.10, "changePercent": 0.80, "updatedAt": "..." },
    { "symbol": "^IXIC", "name": "Nasdaq", "price": 16580.23, ... },
    { "symbol": "^DJI", "name": "Dow Jones", "price": 39821.10, ... },
    { "symbol": "^VIX", "name": "VIX", "price": 14.52, ... }
  ],
  "usdKrw": 1385.20,
  "usdKrwChange": -3.50,
  "macro": [
    { "symbol": "^TNX", "name": "10Y Treasury", "price": 4.42, "change": 0.03, "changePercent": 0.68, "updatedAt": "..." },
    { "symbol": "GC=F", "name": "Gold", "price": 2348.50, "change": 12.30, "changePercent": 0.53, "updatedAt": "..." },
    { "symbol": "CL=F", "name": "WTI Oil", "price": 78.25, "change": -0.45, "changePercent": -0.57, "updatedAt": "..." }
  ],
  "updatedAt": "2026-04-23T15:30:00Z",
  "disclaimer": "..."
}
```

### 5.2 GET /api/v1/market/sectors (신규)

**Response**:
```json
[
  { "sector": "Technology", "sectorKo": "기술", "changePercent": 1.24 },
  { "sector": "Energy", "sectorKo": "에너지", "changePercent": 1.15 },
  { "sector": "Healthcare", "sectorKo": "헬스케어", "changePercent": 0.82 },
  ...
]
```

**캐시**: 15분 TTL (`market:sectors`)
**에러**: FMP + Yahoo 모두 실패 시 빈 배열 `[]` 반환 (500 아님)

---

## 6. Cache Strategy

| 키 | TTL | 소스 | 비고 |
|----|-----|------|------|
| `market:overview` | 5분 | Finnhub → Yahoo → TwelveData | 기존 + macro 추가 |
| `market:sectors` | 15분 | FMP → Yahoo ETFs | 신규, 변동 빈도 낮음 |
| `market:news` | 15분 | Finnhub | 기존 — 변경 없음 |
| `market:movers` | 5분 | FMP | 기존 — 변경 없음 |

### FMP 요청 예산 분석

| 호출 | 빈도 | 일간 최대 |
|------|------|----------|
| /sector-performance | 15분마다 | 96회 |
| /biggest-gainers | 5분마다 | 288회 |
| /biggest-losers | 5분마다 | 288회 |
| /profile (overview) | 24시간 | ~10회 |
| **합계** | | **~682회** |

> 250 req/day 제한 초과 위험. 실제로는 캐시 히트 + 방문 트리거 방식이므로 문제 없으나,
> 만약 다수 사용자가 캐시 미스 시점에 동시 접속 시 단일 요청만 통과 (RedisCacheAdapter의 getOrLoad 패턴).
> **Movers의 FMP 호출은 이미 기존 구현이므로 섹터 추가분은 최대 96회/일.**

---

## 7. Implementation Order

| Step | 파일 | 작업 | 의존성 |
|------|------|------|--------|
| 1 | `SectorPerformance.java` | 도메인 record 생성 | — |
| 2 | `FmpClient.java` | `sectorPerformance()` + `FmpSectorPerformance` record 추가 | — |
| 3 | `SectorPerformanceService.java` | FMP primary + Yahoo ETF fallback + Redis 15분 캐시 | Step 1, 2 |
| 4 | `MarketOverviewResponse.java` | `macro` 필드 추가 | — |
| 5 | `MarketOverviewService.java` | `MACRO_SYMBOLS` + `fetchMacro()` 추가 | Step 4 |
| 6 | `MarketController.java` | `sectors()` 엔드포인트 + `SectorPerformanceService` 주입 | Step 3 |
| 7 | `types/market.ts` | `SectorPerformance` 타입 + `MarketOverview.macro` 확장 | — |
| 8 | `lib/api/market.ts` | `getSectorPerformance()` 함수 추가 | Step 7 |
| 9 | `use-sector-performance.ts` | React Query 훅 (15분 staleTime) | Step 8 |
| 10 | `market-overview.tsx` | 지수/매크로 섹션 분리 + SectionLabel + MacroCard | Step 7 |
| 11 | `sector-performance.tsx` | SectorChip 위젯 (가로 스크롤 바 차트) | Step 9 |
| 12 | `market-dashboard.tsx` | 레이아웃 재구성 (지수→매크로→섹터→급등락+뉴스) | Step 10, 11 |
| 13 | 검증 | `make web-check` + `make api-check` + 브라우저 확인 | Step 12 |

---

## 8. Error Handling

| 시나리오 | BE 처리 | FE 결과 |
|---------|---------|---------|
| FMP /sector-performance 429 | Yahoo ETF fallback | 섹터 정상 표시 |
| FMP + Yahoo 모두 실패 | `[]` 빈 배열 반환 | 섹터 섹션 숨김 |
| 10Y/Gold/WTI 3-tier 모두 실패 | macro 배열에서 제외 | 해당 카드만 숨김 |
| 전체 overview API 실패 | 500 + UPSTREAM_UNAVAILABLE | 에러 메시지 + 재시도 버튼 |

---

## 9. Testing Checklist

| # | 항목 | 방법 |
|---|------|------|
| T-01 | FMP /sector-performance 응답 파싱 | dev 서버 + curl |
| T-02 | SectorPerformanceService Redis 캐시 | 2회 호출 → 1회만 FMP |
| T-03 | GET /api/v1/market/sectors 엔드포인트 | curl → 11개 섹터 반환 |
| T-04 | MarketOverviewResponse.macro 포함 | curl /overview → macro 필드 존재 |
| T-05 | FE 지수 섹션 분리 표시 | 브라우저 확인 |
| T-06 | FE 매크로 섹션 (10Y, Gold, WTI, USD/KRW) | 브라우저 확인 |
| T-07 | FE 섹터 칩 표시 + 정렬 | 브라우저 확인 |
| T-08 | 모바일 섹터 가로 스크롤 | 375px DevTools |
| T-09 | FMP 실패 시 graceful degradation | API key 제거 테스트 |
| T-10 | `make web-check` + `make api-check` | CI 등가 검증 |

---

## 10. Files Summary

### 신규 파일 (4)
- `apps/api/src/main/java/com/aistockadvisor/market/domain/SectorPerformance.java`
- `apps/api/src/main/java/com/aistockadvisor/market/service/SectorPerformanceService.java`
- `apps/web/src/features/market-dashboard/sector-performance.tsx`
- `apps/web/src/features/market-dashboard/hooks/use-sector-performance.ts`

### 수정 파일 (7)
- `apps/api/src/main/java/com/aistockadvisor/market/infra/FmpClient.java` — sectorPerformance() 추가
- `apps/api/src/main/java/com/aistockadvisor/market/domain/MarketOverviewResponse.java` — macro 필드
- `apps/api/src/main/java/com/aistockadvisor/market/service/MarketOverviewService.java` — fetchMacro()
- `apps/api/src/main/java/com/aistockadvisor/market/web/MarketController.java` — sectors() 엔드포인트
- `apps/web/src/types/market.ts` — SectorPerformance, macro 타입
- `apps/web/src/lib/api/market.ts` — getSectorPerformance()
- `apps/web/src/features/market-dashboard/market-overview.tsx` — 섹션 분리
- `apps/web/src/features/market-dashboard/market-dashboard.tsx` — 레이아웃

**합계**: 신규 4 + 수정 8 = **12 파일**, 예상 **+600~900 lines**
