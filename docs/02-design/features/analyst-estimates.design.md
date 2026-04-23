# Design: analyst-estimates

## Executive Summary

| 관점 | 요약 |
|---|---|
| **Problem** | 종목 상세에 펀더멘털(P/E, 52주 고저)은 있지만, 월가 애널리스트 컨센서스·목표가·분기 실적 정보가 없어 초보 투자자가 시장 기대치를 파악할 수 없음 |
| **Solution** | Yahoo Finance quoteSummary 모듈 확장(financialData + recommendationTrend + earningsHistory) + FMP fallback으로 애널리스트 데이터 수집, Redis 24h 캐시 |
| **Function UX Effect** | 종목 상세에 "애널리스트 컨센서스" 패널 추가 — 평점 게이지 + 목표가 레인지 바 + 분기 실적 Beat/Miss 히스토리 |
| **Core Value** | "전문가들은 이 종목을 어떻게 보나?" 질문에 즉답 — 펀더멘털 → 시장 기대치로 판단 맥락 확장 |

---

## 1. API Design

### 1.1 Endpoint

```
GET /api/v1/stocks/{ticker}/analyst
```

| 항목 | 값 |
|------|-----|
| Method | GET |
| Path | `/api/v1/stocks/{ticker}/analyst` |
| Path Param | `ticker` — `^[A-Z]{1,5}(\.[A-Z])?$` (기존 TICKER_REGEX 재활용) |
| Auth | 불필요 (공개 API) |
| Cache | Redis 24시간 TTL |

### 1.2 Response Schema

```json
{
  "rating": {
    "score": 2.1,
    "label": "Buy",
    "labelKo": "매수",
    "totalAnalysts": 42,
    "distribution": {
      "strongBuy": 15,
      "buy": 12,
      "hold": 10,
      "sell": 3,
      "strongSell": 2
    }
  },
  "priceTarget": {
    "current": 185.50,
    "high": 250.00,
    "low": 140.00,
    "mean": 210.75,
    "median": 215.00,
    "upsidePercent": 13.6
  },
  "earnings": [
    {
      "quarter": "Q1 2025",
      "epsActual": 1.58,
      "epsEstimate": 1.50,
      "surprisePercent": 5.33,
      "result": "BEAT"
    },
    {
      "quarter": "Q4 2024",
      "epsActual": 2.18,
      "epsEstimate": 2.35,
      "surprisePercent": -7.23,
      "result": "MISS"
    }
  ]
}
```

### 1.3 Null Handling

| 필드 | null 조건 | FE 동작 |
|------|-----------|---------|
| `rating` | 애널리스트 데이터 없음 (소형주/ETF) | 전체 패널 숨김 |
| `priceTarget` | 목표가 데이터 없음 | 해당 섹션 숨김 |
| `earnings` | 실적 데이터 없음 (빈 배열) | 해당 섹션 숨김 |

전체 응답이 null → HTTP 200 + `null` body → FE 패널 미렌더링 (graceful degradation).

### 1.4 Rating Label 매핑

| score 범위 | label | labelKo |
|-----------|-------|---------|
| 1.0 ~ 1.5 | Strong Buy | 적극 매수 |
| 1.5 ~ 2.5 | Buy | 매수 |
| 2.5 ~ 3.5 | Hold | 보유 |
| 3.5 ~ 4.5 | Sell | 매도 |
| 4.5 ~ 5.0 | Strong Sell | 적극 매도 |

### 1.5 Earnings Result 판정

| 조건 | result |
|------|--------|
| `surprisePercent > 1.0` | BEAT |
| `surprisePercent < -1.0` | MISS |
| `-1.0 ≤ surprisePercent ≤ 1.0` | MEET |

---

## 2. Data Sources

### 2.1 Yahoo Finance quoteSummary (Primary)

기존 `quoteSummary()` 메서드의 `SUMMARY_MODULES` 상수에 3개 모듈 추가:

```
기존: summaryDetail,defaultKeyStatistics,assetProfile
확장: summaryDetail,defaultKeyStatistics,assetProfile,financialData,recommendationTrend,earningsHistory
```

#### financialData 파싱 경로

```
result[0].financialData
  ├── currentPrice.raw          → priceTarget.current
  ├── targetHighPrice.raw       → priceTarget.high
  ├── targetLowPrice.raw        → priceTarget.low
  ├── targetMeanPrice.raw       → priceTarget.mean
  ├── targetMedianPrice.raw     → priceTarget.median
  ├── recommendationMean.raw    → rating.score
  ├── recommendationKey         → rating.label 참조용
  └── numberOfAnalystOpinions.raw → rating.totalAnalysts
```

#### recommendationTrend 파싱 경로

```
result[0].recommendationTrend.trend[0]   (가장 최근 기간)
  ├── strongBuy    → rating.distribution.strongBuy
  ├── buy          → rating.distribution.buy
  ├── hold         → rating.distribution.hold
  ├── sell         → rating.distribution.sell
  └── strongSell   → rating.distribution.strongSell
```

#### earningsHistory 파싱 경로

```
result[0].earningsHistory.history[0..3]   (최근 4분기)
  ├── quarter.fmt            → earnings[].quarter (e.g. "1Q2025")
  ├── epsActual.raw          → earnings[].epsActual
  ├── epsEstimate.raw        → earnings[].epsEstimate
  ├── epsDifference.raw      → (계산용)
  └── surprisePercent.raw    → earnings[].surprisePercent (×100)
```

### 2.2 FMP (Fallback)

Yahoo 실패 시 3개 FMP 엔드포인트를 병렬 호출:

| FMP 엔드포인트 | 매핑 | 비고 |
|---------------|------|------|
| `GET /v3/analyst-stock-recommendations/{ticker}?apikey=KEY` | rating (최근 레코드에서 집계) | 개별 애널리스트 리스트 → 분포 집계 |
| `GET /v4/price-target-consensus?symbol={ticker}&apikey=KEY` | priceTarget (직접 매핑) | targetHigh, targetLow, targetConsensus, targetMedian |
| `GET /v3/earnings-surprises/{ticker}?apikey=KEY` | earnings (최근 4건) | actualEarningResult, estimatedEarning |

### 2.3 Fallback 체인

```
Yahoo quoteSummary (financialData + recommendationTrend + earningsHistory)
  ↓ null 또는 예외 시
FMP (3개 엔드포인트 병렬)
  ↓ 모두 실패 시
null → FE 패널 숨김
```

### 2.4 캐시 전략

| Redis 키 | TTL | 근거 |
|----------|-----|------|
| `analyst:{ticker}` | 24시간 | 애널리스트 데이터는 일 단위 갱신, 호출 예산 절약 |

기존 `RedisCacheAdapter.getOrLoad()` 패턴 재활용.

---

## 3. BE Implementation

### 3.1 Domain Record

**`com.aistockadvisor.stock.domain.AnalystEstimates`**

```java
public record AnalystEstimates(
    Rating rating,
    PriceTarget priceTarget,
    List<EarningsQuarter> earnings
) {
    public record Rating(
        BigDecimal score,
        String label,
        String labelKo,
        Integer totalAnalysts,
        Distribution distribution
    ) {
        public record Distribution(
            int strongBuy, int buy, int hold, int sell, int strongSell
        ) {}
    }

    public record PriceTarget(
        BigDecimal current,
        BigDecimal high,
        BigDecimal low,
        BigDecimal mean,
        BigDecimal median,
        BigDecimal upsidePercent
    ) {}

    public record EarningsQuarter(
        String quarter,
        BigDecimal epsActual,
        BigDecimal epsEstimate,
        BigDecimal surprisePercent,
        String result  // "BEAT" | "MISS" | "MEET"
    ) {}
}
```

### 3.2 Service

**`com.aistockadvisor.stock.service.AnalystEstimatesService`**

```java
@Service
public class AnalystEstimatesService {

    private final YahooFinanceClient yahooClient;
    private final FmpClient fmpClient;
    private final RedisCacheAdapter cache;

    private static final Duration CACHE_TTL = Duration.ofHours(24);

    public AnalystEstimates getEstimates(String ticker) {
        return cache.getOrLoad(
            "analyst:" + ticker,
            AnalystEstimates.class,
            CACHE_TTL,
            () -> fetchEstimates(ticker)
        );
    }

    private AnalystEstimates fetchEstimates(String ticker) {
        // 1. Yahoo primary
        AnalystEstimates yahoo = yahooClient.analystEstimates(ticker);
        if (yahoo != null) return yahoo;

        // 2. FMP fallback
        return fetchFromFmp(ticker);
    }
}
```

### 3.3 YahooFinanceClient 확장

기존 `quoteSummary()` 메서드는 `CompanyOverview`를 반환. 새 메서드 `analystEstimates()`를 추가하여 확장 모듈을 요청.

```java
private static final String ANALYST_MODULES =
    "financialData,recommendationTrend,earningsHistory";

public AnalystEstimates analystEstimates(String ticker) {
    // ensureCrumb() → query2Client GET /v10/finance/quoteSummary
    // modules = ANALYST_MODULES, crumb 인증
    // parseAnalystData(result[0]) → AnalystEstimates
}
```

기존 `quoteSummary()`의 crumb 인증/에러 핸들링 패턴을 동일하게 적용.

### 3.4 FmpClient 확장

**3개 메서드 추가** (기존 `FmpClient`에):

```java
// 1. 애널리스트 추천 리스트 → 분포 집계용
public List<FmpAnalystRecommendation> analystRecommendations(String ticker)

// 2. 목표가 컨센서스
public FmpPriceTargetConsensus priceTargetConsensus(String ticker)

// 3. 실적 서프라이즈
public List<FmpEarningsSurprise> earningsSurprises(String ticker)
```

**FMP DTO Records** (FmpClient 내부):

```java
@JsonIgnoreProperties(ignoreUnknown = true)
public record FmpAnalystRecommendation(
    String analystName,
    String recommendationKey  // "Buy", "Sell", "Hold", etc.
) {}

@JsonIgnoreProperties(ignoreUnknown = true)
public record FmpPriceTargetConsensus(
    BigDecimal targetHigh,
    BigDecimal targetLow,
    BigDecimal targetConsensus,
    BigDecimal targetMedian
) {}

@JsonIgnoreProperties(ignoreUnknown = true)
public record FmpEarningsSurprise(
    String date,
    BigDecimal actualEarningResult,
    BigDecimal estimatedEarning
) {}
```

### 3.5 Controller 확장

**`StockController`에 엔드포인트 1개 추가**:

```java
@GetMapping("/{ticker}/analyst")
public AnalystEstimates analyst(
    @PathVariable("ticker") @Pattern(regexp = TICKER_REGEX) String ticker) {
    return analystService.getEstimates(ticker);
}
```

### 3.6 파일 변경 목록 (BE)

| 파일 | 변경 유형 | 설명 |
|------|----------|------|
| `stock/domain/AnalystEstimates.java` | **신규** | 도메인 record (Rating, PriceTarget, EarningsQuarter) |
| `stock/service/AnalystEstimatesService.java` | **신규** | Yahoo primary + FMP fallback + 24h 캐시 |
| `stock/infra/client/YahooFinanceClient.java` | **수정** | `analystEstimates()` 메서드 + 파싱 로직 추가 |
| `market/infra/FmpClient.java` | **수정** | 3개 엔드포인트 + 3개 DTO record 추가 |
| `stock/web/StockController.java` | **수정** | `GET /{ticker}/analyst` 엔드포인트 추가 |

---

## 4. FE Implementation

### 4.1 Type Definition

**`types/stock.ts` 추가**:

```typescript
export interface AnalystEstimates {
  rating: {
    score: number;
    label: string;
    labelKo: string;
    totalAnalysts: number;
    distribution: {
      strongBuy: number;
      buy: number;
      hold: number;
      sell: number;
      strongSell: number;
    };
  } | null;
  priceTarget: {
    current: number;
    high: number;
    low: number;
    mean: number;
    median: number;
    upsidePercent: number;
  } | null;
  earnings: {
    quarter: string;
    epsActual: number;
    epsEstimate: number;
    surprisePercent: number;
    result: 'BEAT' | 'MISS' | 'MEET';
  }[];
}
```

### 4.2 API Function

**`lib/api/stocks.ts` 추가**:

```typescript
export function getAnalystEstimates(ticker: string): Promise<AnalystEstimates | null> {
  return apiFetch<AnalystEstimates | null>(`/stocks/${ticker}/analyst`);
}
```

### 4.3 React Query Hook

**`features/stock-detail/analyst/hooks/use-analyst-estimates.ts`** (신규):

```typescript
export function useAnalystEstimates(ticker: string) {
  return useQuery<AnalystEstimates | null>({
    queryKey: ['analyst', ticker],
    queryFn: () => getAnalystEstimates(ticker),
    staleTime: 24 * 60 * 60 * 1000,  // 24h (캐시 TTL과 동기화)
    retry: 1,
  });
}
```

### 4.4 Component Hierarchy

```
StockDetailView
  └── AnalystPanel                     ← 신규 (CompanyOverviewPanel 아래)
        ├── RatingGauge                 ← 평점 게이지 섹션
        │     ├── 점수 원형 게이지 (1.0~5.0)
        │     ├── 한국어 라벨 + 총 애널리스트 수
        │     └── 분포 막대 차트 (5단계)
        ├── PriceTargetBar              ← 목표가 레인지 섹션
        │     ├── 현재가 vs Mean 목표가 비교
        │     ├── High / Mean / Low 레인지 바
        │     └── Upside/Downside % 배지
        └── EarningsHistory             ← 분기 실적 섹션
              └── 4분기 EPS 테이블 (Actual vs Estimate + Beat/Miss)
```

### 4.5 Component Specifications

#### 4.5.1 AnalystPanel

**파일**: `features/stock-detail/analyst/analyst-panel.tsx`

```
┌─────────────────────────────────────────────────┐
│ 애널리스트 컨센서스          ⓘ                   │
│                                                  │
│ ┌──────────────┐ ┌──────────────────────────────┐│
│ │  RatingGauge │ │  PriceTargetBar              ││
│ └──────────────┘ └──────────────────────────────┘│
│                                                  │
│ ┌──────────────────────────────────────────────┐ │
│ │  EarningsHistory                              │ │
│ └──────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────┘
```

- 데이터가 null이면 전체 패널 미렌더링 (`return null`)
- 각 섹션은 해당 데이터가 null/빈 배열이면 개별 숨김
- 패널 헤더에 `InfoTooltip` ("월가 애널리스트들의 종합 의견입니다. 투자 판단의 참고 자료로만 활용하세요.")

#### 4.5.2 RatingGauge

**파일**: `features/stock-detail/analyst/components/rating-gauge.tsx`

```
┌──────────────────────────────────────────────┐
│ 투자 의견          ⓘ                         │
│                                               │
│    ┌─────────┐                                │
│    │  2.1    │  매수 (Buy)                    │
│    │  /5.0   │  42명의 애널리스트              │
│    └─────────┘                                │
│                                               │
│  적극매수 ████████████████  15                 │
│  매수     ████████████     12                  │
│  보유     ██████████       10                  │
│  매도     ███               3                  │
│  적극매도 ██                2                  │
└──────────────────────────────────────────────┘
```

Props:
```typescript
interface RatingGaugeProps {
  score: number;         // 1.0 ~ 5.0
  label: string;
  labelKo: string;
  totalAnalysts: number;
  distribution: {
    strongBuy: number; buy: number; hold: number;
    sell: number; strongSell: number;
  };
}
```

- 점수 원형 배지: `bg-primary` 원 안에 점수 표시
- 점수 색상: 1.0~2.5 → `text-green-600`, 2.5~3.5 → `text-yellow-600`, 3.5~5.0 → `text-red-600`
- 분포 막대: 각 카테고리별 비율 계산, `max` 기준 proportional width
- 막대 색상: strongBuy/buy → `bg-green-500`, hold → `bg-yellow-500`, sell/strongSell → `bg-red-500`

#### 4.5.3 PriceTargetBar

**파일**: `features/stock-detail/analyst/components/price-target-bar.tsx`

```
┌──────────────────────────────────────────────┐
│ 목표가          ⓘ                             │
│                                               │
│  현재가 $185.50  →  평균 목표가 $210.75        │
│                     Upside +13.6%             │
│                                               │
│  $140 ├──────────●──────────────────┤ $250    │
│  Low      현재가 ▲         Mean ◆      High   │
└──────────────────────────────────────────────┘
```

Props:
```typescript
interface PriceTargetBarProps {
  current: number;
  high: number;
  low: number;
  mean: number;
  upsidePercent: number;
}
```

- 레인지 바: `Week52RangeBar`와 동일한 패턴 (bg-bg-muted + 마커)
- 현재가 마커: `bg-primary` 원형, absolute positioning (`left: (current-low)/(high-low) * 100%`)
- Mean 마커: `bg-accent` 다이아몬드, absolute positioning
- Upside 배지: 양수 → `text-green-600 bg-green-50`, 음수 → `text-red-600 bg-red-50`

#### 4.5.4 EarningsHistory

**파일**: `features/stock-detail/analyst/components/earnings-history.tsx`

```
┌──────────────────────────────────────────────┐
│ 분기 실적          ⓘ                         │
│                                               │
│  분기      예상     실제      서프라이즈        │
│  ─────────────────────────────────────────── │
│  Q1 2025   $1.50    $1.58    +5.3%  ● BEAT   │
│  Q4 2024   $2.35    $2.18    -7.2%  ● MISS   │
│  Q3 2024   $1.42    $1.43    +0.7%  ● MEET   │
│  Q2 2024   $1.33    $1.40    +5.3%  ● BEAT   │
└──────────────────────────────────────────────┘
```

Props:
```typescript
interface EarningsHistoryProps {
  earnings: {
    quarter: string;
    epsActual: number;
    epsEstimate: number;
    surprisePercent: number;
    result: 'BEAT' | 'MISS' | 'MEET';
  }[];
}
```

- 테이블 형태 (모바일: 카드 형태 전환)
- Result 라벨 색상: BEAT → `text-green-600`, MISS → `text-red-600`, MEET → `text-yellow-600`
- `●` 원형 인디케이터 + 라벨 텍스트

### 4.6 Tooltip 텍스트

| 섹션 | 한국어 툴팁 |
|------|------------|
| 패널 헤더 | 월가 애널리스트들의 종합 의견입니다. 투자 판단의 참고 자료로만 활용하세요. |
| 투자 의견 | 애널리스트들이 매수(Buy) ~ 매도(Sell) 중 어디에 투표했는지 보여줍니다. 1.0에 가까울수록 매수 의견이 강합니다. |
| 목표가 | 애널리스트들이 예상하는 향후 12개월 목표 주가입니다. 현재가와 비교해 상승(Upside) 또는 하락(Downside) 여력을 확인할 수 있습니다. |
| 분기 실적 | 최근 4분기 주당순이익(EPS) 실적입니다. 예상치를 넘으면 Beat(초과), 못 미치면 Miss(미달)입니다. 실적 발표는 주가에 큰 영향을 줍니다. |

### 4.7 Responsive Layout

| 화면 | 레이아웃 |
|------|---------|
| `≥ 768px` (sm+) | RatingGauge + PriceTargetBar 2열, EarningsHistory 1열 전체 |
| `< 768px` | 모두 1열 스택 |

```tsx
<div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
  <RatingGauge ... />
  <PriceTargetBar ... />
</div>
<EarningsHistory ... />
```

### 4.8 파일 변경 목록 (FE)

| 파일 | 변경 유형 | 설명 |
|------|----------|------|
| `types/stock.ts` | **수정** | `AnalystEstimates` 인터페이스 추가 |
| `lib/api/stocks.ts` | **수정** | `getAnalystEstimates()` 함수 추가 |
| `features/stock-detail/analyst/hooks/use-analyst-estimates.ts` | **신규** | React Query 훅 |
| `features/stock-detail/analyst/analyst-panel.tsx` | **신규** | 메인 패널 컨테이너 |
| `features/stock-detail/analyst/components/rating-gauge.tsx` | **신규** | 평점 게이지 |
| `features/stock-detail/analyst/components/price-target-bar.tsx` | **신규** | 목표가 레인지 바 |
| `features/stock-detail/analyst/components/earnings-history.tsx` | **신규** | 분기 실적 테이블 |
| `features/stock-detail/stock-detail-view.tsx` | **수정** | AnalystPanel 배치 (CompanyOverviewPanel 아래) |

---

## 5. Implementation Order

### Step 1: BE Domain + Service (BE)
1. `AnalystEstimates.java` — 도메인 record 생성
2. `YahooFinanceClient.java` — `analystEstimates()` 메서드 + 파싱 로직
3. `FmpClient.java` — 3개 엔드포인트 + DTO record 추가
4. `AnalystEstimatesService.java` — Yahoo primary + FMP fallback + 캐시
5. `StockController.java` — `GET /{ticker}/analyst` 엔드포인트

### Step 2: FE Type + API + Hook (FE)
6. `types/stock.ts` — `AnalystEstimates` 타입 추가
7. `lib/api/stocks.ts` — `getAnalystEstimates()` 함수
8. `use-analyst-estimates.ts` — React Query 훅

### Step 3: FE Components (FE)
9. `rating-gauge.tsx` — 평점 게이지 컴포넌트
10. `price-target-bar.tsx` — 목표가 레인지 바 컴포넌트
11. `earnings-history.tsx` — 분기 실적 테이블 컴포넌트
12. `analyst-panel.tsx` — 메인 패널 (3개 서브컴포넌트 조합)

### Step 4: Integration (FE)
13. `stock-detail-view.tsx` — AnalystPanel 배치
14. 브라우저 검증 (AAPL, 소형주 graceful degradation)

### Step 5: Verification
15. `make check` (FE tsc + lint, BE check)

---

## 6. Error Handling

| 시나리오 | BE 동작 | FE 동작 |
|---------|---------|---------|
| Yahoo + FMP 모두 실패 | `null` 반환 (예외 전파 안 함) | 패널 미렌더링 |
| Yahoo 401 (crumb 만료) | `invalidateCrumb()` → FMP fallback | 투명 |
| FMP 429 (rate limit) | `null` 반환 (예외 삼킴) | 패널 미렌더링 |
| Redis 장애 | `getOrLoad` fail-open (loader 직접 호출) | 투명 |
| 부분 데이터 (rating만 있고 earnings 없음) | 가용 필드만 채운 AnalystEstimates 반환 | 가용 섹션만 렌더링 |

---

## 7. Testing Checklist

| # | 검증 항목 | 방법 |
|---|----------|------|
| T-1 | AAPL 조회 시 3개 섹션 모두 표시 | 브라우저 |
| T-2 | 소형주(SMCI 등) 데이터 부재 시 패널 숨김 | 브라우저 |
| T-3 | Yahoo 실패 → FMP fallback 동작 | BE 로그 |
| T-4 | Redis 캐시 히트 (24h TTL) | BE 로그 |
| T-5 | 모바일 반응형 (768px 이하 1열) | 브라우저 DevTools |
| T-6 | `tsc --noEmit` 통과 | CLI |
| T-7 | `gradlew check` 통과 | CLI |
| T-8 | Upside/Downside % 정확성 | 계산 검증 |

---

## 8. Dependencies

| 의존성 | 상태 | 비고 |
|--------|------|------|
| Yahoo Finance crumb/cookie 인증 | ✅ 구현 완료 | `YahooFinanceClient.ensureCrumb()` |
| FMP API 키 | ✅ 설정 완료 | `FmpProperties.apiKey` |
| `RedisCacheAdapter` | ✅ 구현 완료 | `getOrLoad()` 패턴 |
| `InfoTooltip` 컴포넌트 | ✅ 구현 완료 | `@floating-ui/react` |
| `apiFetch<T>()` | ✅ 구현 완료 | `lib/api/client.ts` |
| React Query (`@tanstack/react-query`) | ✅ 설정 완료 | Provider 구성됨 |
