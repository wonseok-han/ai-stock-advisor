# yahoo-migration Design Document

> **Summary**: CandleService·MarketOverviewService의 주 데이터 소스를 Yahoo Finance로 전환하고, TwelveData를 최후방 fallback으로 강등. BE만 변경, FE 변경 없음.
>
> **Project**: nowini
> **Version**: v0.1.2
> **Author**: wonseok-han
> **Date**: 2026-04-23
> **Status**: Draft
> **Planning Doc**: [yahoo-migration.plan.md](../../01-plan/features/yahoo-migration.plan.md)

---

## 1. Overview

### 1.1 Design Goals

- Yahoo Finance를 인트라데이 캔들·시장 지수·환율의 **주 소스**로 사용하여 TwelveData rate limit(8 req/min) 부담 해소.
- TwelveDataClient 코드·설정을 **보존**하되, 일상 호출에서 빠지고 Yahoo 장애 시 최후방 fallback으로만 동작.
- **FE 변경 0건** — API 응답 스키마(Quote, Candle, MarketOverviewResponse) 그대로 유지.
- 기존 Redis 캐시 전략(키·TTL) 유지.

### 1.2 Design Principles

- **Primary → Fallback 체인** — Yahoo(무제한) 우선, 실패 시 TwelveData. MarketOverview는 Finnhub → Yahoo → TwelveData 3단.
- **기존 코드 최소 변경** — YahooFinanceClient에 메서드 1개 추가, Service 2개에서 호출 순서만 변경.
- **Defensive parsing** — Yahoo 응답 파싱 실패 시 빈 리스트/null 반환, 예외 전파 없이 fallback으로 넘김.

---

## 2. Architecture

### 2.1 Fallback Chain (변경 전/후)

```
┌─────────────────────────────────────────────────────────────┐
│  CandleService.getIntradayCandles()                         │
│                                                             │
│  Before:  TwelveData ──(fail)──→ TICKER_NOT_FOUND           │
│                                                             │
│  After:   Yahoo Finance ──(fail)──→ TwelveData ──(fail)──→  │
│           TICKER_NOT_FOUND                                  │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│  MarketOverviewService.fetchIndex()                         │
│                                                             │
│  Before:  Finnhub ──(fail)──→ TwelveData ──(fail)──→ null   │
│                                                             │
│  After:   Finnhub ──(fail)──→ Yahoo Finance ──(fail)──→     │
│           TwelveData ──(fail)──→ null                       │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│  MarketOverviewService.fetchUsdKrw()                        │
│                                                             │
│  Before:  Finnhub ──(fail)──→ TwelveData ──(fail)──→ null   │
│                                                             │
│  After:   Finnhub ──(fail)──→ Yahoo Finance ──(fail)──→     │
│           TwelveData ──(fail)──→ null                       │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 Component Dependency

```
CandleService
  ├── YahooFinanceClient   (주: 인트라데이 5분봉)
  ├── TwelveDataClient     (fallback: 인트라데이)
  ├── CandleRepository     (DB: 일봉+)
  └── RedisCacheAdapter

MarketOverviewService
  ├── FinnhubClient        (주: 지수/환율)
  ├── YahooFinanceClient   (2차 fallback)
  ├── TwelveDataClient     (3차 fallback)
  └── RedisCacheAdapter
```

---

## 3. Detailed Design

### 3.1 YahooFinanceClient — `fetchIntradayCandles()` 추가

**새 메서드 시그니처:**

```java
public List<Candle> fetchIntradayCandles(String ticker)
```

**Yahoo Finance API 호출:**

```
GET /v8/finance/chart/{ticker}?interval=5m&range=1d
```

**응답 파싱 로직:**

```
root.chart.result[0].timestamp[]        → epoch seconds
root.chart.result[0].indicators.quote[0]
  .open[], .high[], .low[], .close[], .volume[]
```

- 기존 `parseChartResponse()`는 `CandleEntity` (DB용) 반환 → 인트라데이용은 `Candle` (도메인) 직접 반환
- 별도 private 메서드 `parseIntradayResponse()` 추가
- null/empty 시 `Collections.emptyList()` 반환 (예외 전파 없음)

### 3.2 CandleService 변경

**Before (line 69-76):**
```java
private List<Candle> getIntradayCandles(String ticker, TimeFrame tf) {
    String key = "candle:" + ticker + ":" + tf.code();
    List<Candle> candles = cache.getOrLoad(key, LIST_TYPE, TTL_INTRADAY,
            () -> twelveData.timeSeries(ticker, tf.twelveDataInterval(), tf.outputSize()));
    ...
}
```

**After:**
```java
private List<Candle> getIntradayCandles(String ticker, TimeFrame tf) {
    String key = "candle:" + ticker + ":" + tf.code();
    List<Candle> candles = cache.getOrLoad(key, LIST_TYPE, TTL_INTRADAY,
            () -> fetchIntradayWithFallback(ticker, tf));
    ...
}

private List<Candle> fetchIntradayWithFallback(String ticker, TimeFrame tf) {
    // 1차: Yahoo Finance
    List<Candle> candles = yahooFinance.fetchIntradayCandles(ticker);
    if (candles != null && !candles.isEmpty()) {
        return candles;
    }
    // 2차: TwelveData fallback
    log.info("yahoo intraday empty for {}, falling back to twelvedata", ticker);
    return twelveData.timeSeries(ticker, tf.twelveDataInterval(), tf.outputSize());
}
```

**변경 포인트:**
- `cache.getOrLoad()` 의 loader 함수만 교체
- Redis 캐시 키·TTL 변경 없음
- fallback 시 기존 TwelveData 호출 그대로 보존

### 3.3 MarketOverviewService 변경

**3.3.1 생성자 — YahooFinanceClient 주입 추가**

```java
// Before: FinnhubClient + TwelveDataClient
// After:  FinnhubClient + YahooFinanceClient + TwelveDataClient

public MarketOverviewService(FinnhubClient finnhubClient,
                             YahooFinanceClient yahooFinanceClient,
                             TwelveDataClient twelveDataClient,
                             RedisCacheAdapter cache)
```

**3.3.2 INDEX_SYMBOLS — Yahoo 심볼 컬럼 추가**

```java
// Before: {Finnhub, TwelveData, DisplayName}
// After:  {Finnhub, Yahoo, TwelveData, DisplayName}

private static final String[][] INDEX_SYMBOLS = {
    {"^GSPC", "^GSPC",     "SPX", "S&P 500"},
    {"^IXIC", "^IXIC",     "IXIC", "Nasdaq"},
    {"^DJI",  "^DJI",      "DJI", "Dow Jones"},
    {"^VIX",  "^VIX",      "VIX", "VIX"},
};
```

**3.3.3 fetchIndex() — 3단 fallback**

```java
private MarketIndex fetchIndex(String finnhubSymbol, String yahooSymbol,
                                String twelveSymbol, String displayName) {
    // 1차: Finnhub
    Quote q = tryQuote(() -> finnhubClient.quote(finnhubSymbol));
    if (q != null) return toMarketIndex(finnhubSymbol, displayName, q);

    // 2차: Yahoo Finance
    q = tryQuote(() -> yahooFinanceClient.quote(yahooSymbol));
    if (q != null) return toMarketIndex(yahooSymbol, displayName, q);

    // 3차: TwelveData (최후방)
    q = tryQuote(() -> twelveDataClient.quote(twelveSymbol));
    if (q != null) return toMarketIndex(twelveSymbol, displayName, q);

    log.warn("index {} unavailable from all sources", displayName);
    return null;
}
```

**3.3.4 fetchUsdKrw() — 3단 fallback**

```java
// 1차: Finnhub ("USDKRW=X")
// 2차: Yahoo Finance ("USDKRW=X")
// 3차: TwelveData ("USD/KRW")
```

**3.3.5 tryQuote() — 공통 에러 핸들링 헬퍼**

```java
private Quote tryQuote(Supplier<Quote> supplier) {
    try {
        Quote q = supplier.get();
        if (q != null && q.price() != null && q.price().signum() > 0) {
            return q;
        }
    } catch (BusinessException ex) {
        log.debug("quote fallback: {}", ex.getMessage());
    }
    return null;
}
```

기존의 try-catch 중복 코드를 `tryQuote()` 헬퍼로 통합.

### 3.4 TimeFrame enum

**변경 최소화:** 기존 `twelveDataInterval()` 보존, `yahooInterval()` 추가.

```java
D1("5min", "5m",  78,    1, false),
W1("1day", "1d",   5,    7,  true),
// ...

private final String twelveDataInterval;
private final String yahooInterval;

public String yahooInterval() { return yahooInterval; }
```

- CandleService가 Yahoo 호출 시 `tf.yahooInterval()` 사용 가능 (현재는 D1만 인트라데이이므로 하드코딩 "5m"도 가능)
- TwelveData fallback 시 기존 `tf.twelveDataInterval()` 그대로 사용

---

## 4. Implementation Order

| Step | 작업 | 파일 | 의존 |
|------|------|------|------|
| 1 | `YahooFinanceClient.fetchIntradayCandles()` 추가 | `YahooFinanceClient.java` | 없음 |
| 2 | `TimeFrame`에 `yahooInterval` 필드 추가 | `TimeFrame.java` | 없음 |
| 3 | `CandleService` Yahoo 우선 + TwelveData fallback | `CandleService.java` | Step 1 |
| 4 | `MarketOverviewService` 3단 fallback + `tryQuote()` 헬퍼 | `MarketOverviewService.java` | 없음 |
| 5 | 빌드 확인 (`./gradlew build`) | — | Step 1-4 |

---

## 5. Verification

### 5.1 빌드 검증

```bash
cd apps/api && ./gradlew build -x test
```

### 5.2 수동 테스트 (로컬 서버)

| 테스트 | API | 기대 결과 |
|--------|-----|-----------|
| D1 인트라데이 캔들 | `GET /api/v1/stocks/AAPL/candles?tf=1D` | 5분봉 78개 내외 반환 |
| W1 일봉 캔들 | `GET /api/v1/stocks/AAPL/candles?tf=1W` | 기존과 동일 (Yahoo daily, 변경 없음) |
| 시장 지수 | `GET /api/v1/market/overview` | S&P500, Nasdaq, Dow, VIX, USD/KRW 정상 |
| Yahoo 장애 시뮬레이션 | Yahoo 호출 실패 유도 | TwelveData fallback 정상 동작, 로그 확인 |

### 5.3 로그 확인

- 정상 시: Yahoo 호출만 발생, TwelveData 호출 로그 없음
- Yahoo 실패 시: `"yahoo intraday empty for {}, falling back to twelvedata"` 로그 출력
