# Phase 4.5 Improvements Design Document

> **Summary**: 캔들 DB 레이어 + 차트/마이페이지/알림 UX 개선 + 잔여 gap 해소 + Rate Limiter 상세 설계
>
> **Project**: AI Stock Advisor
> **Author**: Claude + wonseok-han
> **Date**: 2026-04-17
> **Status**: Draft
> **Planning Doc**: [phase4.5-improvements.plan.md](../../01-plan/features/phase4.5-improvements.plan.md)

---

## 1. Overview

### 1.1 Design Goals

1. **DB-backed 캔들 레이어**: 무료 API 의존 탈피, 일봉 DB 저장으로 풍부한 차트 데이터 확보
2. **차트 UX 개선**: 타임프레임별 적절한 해상도 매핑, 볼륨 서브차트 정상화
3. **마이페이지 리디자인**: 프로필/북마크(카드)/알림/계정 4섹션 완성도 있는 레이아웃
4. **알림 진입점**: 종목 상세에서 직접 알림 설정 가능하도록 UX 개선
5. **잔여 gap 일괄 해소**: Phase 1~4 Known gaps 클린업
6. **Rate Limiter**: Token Bucket 기반 API 보호 (외부 라이브러리 없이 구현)

### 1.2 Design Principles

- 기존 코드/패턴 재사용 극대화 (RedisCacheAdapter, BusinessException 등)
- DB 일봉이 SoR(Single Source of Record), 주봉/월봉은 서비스 레벨 집계
- FE는 기존 feature-based 구조 유지, 마이페이지만 리디자인

---

## 2. Architecture

### 2.1 캔들 데이터 흐름

```
[On-Demand 로드 - 사용자 첫 조회 시]
  FE → GET /api/v1/stocks/{ticker}/candles?tf=1Y
    → StockController.candles()
    → CandleService.getCandles(ticker, tf)
      ├─ tf == D1 (intraday)
      │    → 기존 로직: TwelveData 5분봉 + Redis 캐시
      ├─ tf ∈ {W1, M1, M3, Y1, Y5} (daily+)
      │    → DB 조회: CandleRepository.findByTickerAndDateBetween()
      │    ├─ 결과 있음 → 바로 반환 (Y5=주봉 집계)
      │    └─ 결과 없음 → on-demand 로드:
      │         → YahooFinanceClient.fetchDailyCandles(ticker, from, to)
      │         → CandleRepository.saveAll() (비동기)
      │         → 즉시 반환

[일간 배치 - 매일 22:00 UTC]
  CandleBatchScheduler
    → DB에 적재된 distinct ticker 목록 조회
    → Yahoo Finance daily API로 당일 캔들 다운로드
    → INSERT ON CONFLICT DO NOTHING
```

> **설계 결정**: 벌크 초기 로드(Python 스크립트) 대신 on-demand 방식 채택.
> 사용자가 종목 페이지를 방문할 때 DB에 데이터가 없으면 Yahoo Finance에서 가져와 비동기 저장.
> 사용자 트래픽 기반으로 자연스럽게 데이터가 축적되므로 불필요한 종목 적재를 방지.

### 2.2 Dependencies

| Component | Depends On | Purpose |
|-----------|-----------|---------|
| CandleService (리팩터) | CandleRepository, TwelveDataClient, YahooFinanceClient | DB-first + API fallback |
| YahooFinanceClient | WebClient, Jackson | Yahoo Finance v8 REST API 호출 |
| CandleBatchScheduler | CandleRepository, YahooFinanceClient | 일간 배치 적재 |
| NotificationSettingModal (FE) | useNotificationSettings, useBookmarks | 종목 상세 알림 모달 |
| RateLimitFilter | ConcurrentHashMap, AtomicLong | IP 기반 Token Bucket 요청 제한 |

---

## 3. Data Model

### 3.1 candles 테이블 (Flyway V8)

```sql
-- V8__candles.sql
CREATE TABLE candles (
    ticker      VARCHAR(10)     NOT NULL,
    trade_date  DATE            NOT NULL,
    open        NUMERIC(12,4)   NOT NULL,
    high        NUMERIC(12,4)   NOT NULL,
    low         NUMERIC(12,4)   NOT NULL,
    close       NUMERIC(12,4)   NOT NULL,
    adj_close   NUMERIC(12,4)   NOT NULL,
    volume      BIGINT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    PRIMARY KEY (ticker, trade_date)
);

COMMENT ON TABLE  candles               IS '일봉 OHLCV + adjusted close. Yahoo Finance SoR.';
COMMENT ON COLUMN candles.adj_close     IS '배당/분할 조정 종가. 차트/지표 계산 기본값.';
COMMENT ON COLUMN candles.trade_date    IS '거래일 (UTC, 주말/공휴일 제외).';

CREATE INDEX idx_candles_ticker_date ON candles (ticker, trade_date DESC);
```

**저장 규모 추정:**
- 1종목 × 5년 일봉 ≈ 1,260 rows × ~80 bytes/row ≈ 100KB
- 30종목 ≈ 3MB, 1,000종목 ≈ 100MB (Supabase 500MB 내 여유)

### 3.2 JPA Entity

```java
package com.aistockadvisor.stock.domain;

@Entity
@Table(name = "candles")
@IdClass(CandleId.class)
public class CandleEntity {
    @Id
    @Column(name = "ticker", length = 10)
    private String ticker;

    @Id
    @Column(name = "trade_date")
    private LocalDate tradeDate;

    @Column(precision = 12, scale = 4, nullable = false)
    private BigDecimal open;

    @Column(precision = 12, scale = 4, nullable = false)
    private BigDecimal high;

    @Column(precision = 12, scale = 4, nullable = false)
    private BigDecimal low;

    @Column(precision = 12, scale = 4, nullable = false)
    private BigDecimal close;

    @Column(name = "adj_close", precision = 12, scale = 4, nullable = false)
    private BigDecimal adjClose;

    @Column(nullable = false)
    private long volume;

    // toCandle() → 기존 Candle record 변환
    public Candle toCandle() {
        long epochSec = tradeDate.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        return new Candle(epochSec, open, high, low, adjClose, volume);
    }
}
```

```java
public record CandleId(String ticker, LocalDate tradeDate) implements Serializable {}
```

### 3.3 Repository

```java
public interface CandleRepository extends JpaRepository<CandleEntity, CandleId> {

    List<CandleEntity> findByTickerAndTradeDateBetweenOrderByTradeDateAsc(
            String ticker, LocalDate from, LocalDate to);

    @Query("SELECT DISTINCT c.ticker FROM CandleEntity c")
    List<String> findDistinctTickers();

    @Query("SELECT MAX(c.tradeDate) FROM CandleEntity c WHERE c.ticker = :ticker")
    Optional<LocalDate> findLatestDate(@Param("ticker") String ticker);
}
```

---

## 4. API Specification

### 4.1 변경되는 엔드포인트

기존 `GET /api/v1/stocks/{ticker}/candles?tf=` 의 동작만 내부적으로 변경 (인터페이스 동일).

| tf | 현재 | 변경 후 |
|----|------|---------|
| 1D | TwelveData 5min × 78 | **변경 없음** (intraday는 API 유지) |
| 1W | TwelveData 30min × 70 | **DB 일봉 5개** (최근 5 거래일) |
| 1M | TwelveData 1day × 30 | **DB 일봉** 최근 22거래�� |
| 3M | TwelveData 1day × 90 | **DB 일봉** 최근 66거래일 |
| 1Y | TwelveData 1day × 260 | **DB 일봉** 최근 252거래일 |
| 5Y | TwelveData 1week × 260 | **DB 일봉 → 서비스 레벨 주봉 집계** 최근 5년 |

### 4.2 TimeFrame enum 변경

```java
public enum TimeFrame {
    D1("5min",  78,  Duration.ofDays(1),   false),   // intraday — API only
    W1("1day",   5,  Duration.ofDays(7),    true),   // DB daily
    M1("1day",  22,  Duration.ofDays(30),   true),   // DB daily
    M3("1day",  66,  Duration.ofDays(90),   true),   // DB daily
    Y1("1day", 252,  Duration.ofDays(365),  true),   // DB daily
    Y5("1day",1260,  Duration.ofDays(1825), true);   // DB daily → weekly 집계

    private final boolean dbBacked;
    // ... 기존 필드 + dbBacked getter 추가
}
```

### 4.3 주봉 집계 로직 (5Y)

```java
// CandleService 내부
private List<Candle> aggregateWeekly(List<CandleEntity> dailies) {
    // ISO Week 기준 그룹핑
    // open = 주 첫 봉 open, close = 주 마지막 봉 adjClose
    // high = max(daily.high), low = min(daily.low)
    // volume = sum(daily.volume)
}
```

---

## 5. Component Design

### 5.1 YahooFinanceClient

```java
package com.aistockadvisor.stock.infra.client;

@Component
public class YahooFinanceClient {
    // Yahoo Finance v8 chart API (무료, key 불필요)
    // GET https://query1.finance.yahoo.com/v8/finance/chart/{symbol}
    //   ?period1={epoch}&period2={epoch}&interval=1d&events=div,splits

    private final WebClient webClient;

    public List<CandleEntity> fetchDailyCandles(String ticker, LocalDate from, LocalDate to) {
        // 1. period1/period2 = epoch seconds
        // 2. JSON 파싱: result[0].indicators.quote[0].{open,high,low,close,volume}
        //              + result[0].indicators.adjclose[0].adjclose
        //              + result[0].timestamp[]
        // 3. CandleEntity 리스트 반환
    }
}
```

**Yahoo Finance v8 응답 구조:**
```json
{
  "chart": {
    "result": [{
      "timestamp": [1609459200, ...],
      "indicators": {
        "quote": [{"open": [...], "high": [...], "low": [...], "close": [...], "volume": [...]}],
        "adjclose": [{"adjclose": [...]}]
      }
    }]
  }
}
```

### 5.2 CandleService 리팩터

```java
@Service
public class CandleService {

    private final TwelveDataClient twelveData;
    private final YahooFinanceClient yahooFinance;
    private final CandleRepository candleRepo;
    private final RedisCacheAdapter cache;

    public List<Candle> getCandles(String ticker, TimeFrame tf) {
        if (!tf.dbBacked()) {
            // intraday (D1) — 기존 로직 유지
            return getIntradayCandles(ticker, tf);
        }
        // daily+ — DB 우선
        return getDailyCandles(ticker, tf);
    }

    private List<Candle> getIntradayCandles(String ticker, TimeFrame tf) {
        // 기존: Redis 캐시 + TwelveData
        String key = "candle:" + ticker + ":" + tf.code();
        return cache.getOrLoad(key, LIST_TYPE, Duration.ofMinutes(5),
                () -> twelveData.timeSeries(ticker, tf.twelveDataInterval(), tf.outputSize()));
    }

    private List<Candle> getDailyCandles(String ticker, TimeFrame tf) {
        LocalDate to = LocalDate.now(ZoneOffset.UTC);
        LocalDate from = to.minus(tf.lookback());

        List<CandleEntity> entities = candleRepo
                .findByTickerAndTradeDateBetweenOrderByTradeDateAsc(ticker, from, to);

        if (entities.isEmpty()) {
            // on-demand: Yahoo에서 로드 → DB 저장 → 반환
            entities = loadAndPersist(ticker, from, to);
        }

        List<Candle> candles = entities.stream()
                .map(CandleEntity::toCandle)
                .toList();

        if (tf == TimeFrame.Y5) {
            candles = aggregateWeekly(entities);
        }

        return candles;
    }

    private List<CandleEntity> loadAndPersist(String ticker, LocalDate from, LocalDate to) {
        List<CandleEntity> fetched = yahooFinance.fetchDailyCandles(ticker, from, to);
        if (!fetched.isEmpty()) {
            // 비동기 저장 (응답 지연 방지)
            CompletableFuture.runAsync(() -> {
                candleRepo.saveAll(fetched); // ON CONFLICT DO NOTHING via @SQLInsert or native
            });
        }
        return fetched;
    }
}
```

### 5.3 CandleBatchScheduler

```java
@Component
public class CandleBatchScheduler {

    private final CandleRepository candleRepo;
    private final YahooFinanceClient yahooFinance;

    // UTC 22:00 = EST 17:00 (미장 마감 후 1시���)
    @Scheduled(cron = "0 0 22 * * MON-FRI")
    public void dailySync() {
        List<String> tickers = candleRepo.findDistinctTickers();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        for (String ticker : tickers) {
            try {
                Optional<LocalDate> lastDate = candleRepo.findLatestDate(ticker);
                LocalDate from = lastDate.map(d -> d.plusDays(1)).orElse(today);
                if (!from.isAfter(today)) {
                    List<CandleEntity> candles = yahooFinance.fetchDailyCandles(ticker, from, today);
                    candleRepo.saveAll(candles);
                }
            } catch (Exception ex) {
                log.warn("candle batch sync failed for {}: {}", ticker, ex.getMessage());
            }
        }
    }
}
```

### 5.4 On-Demand 로드 전략

벌크 초기 로드(Python 스크립트) 대신 **on-demand 방식**을 채택했습니다.

**동작 방식:**
1. 사용자가 종목 페이지 방문 → `CandleService.getDailyCandles()` 호출
2. DB에 해당 종목 데이터 없음 → `loadAndPersist()` 트리거
3. `YahooFinanceClient.fetchDailyCandles(ticker, from, to)` 호출
4. 즉시 결과 반환 + `CompletableFuture.runAsync(() -> candleRepo.saveAll())` 비동기 저장
5. 이후 재방문 시 DB에서 즉시 반환

**장점:**
- Python/yfinance 의존성 제거 (Java 단일 스택)
- 사용자 트래픽 기반 자연 축적 → 불필요 종목 적재 방지
- 배포 즉시 동작 (별도 시드 작업 불필요)

---

## 6. FE UI Design

### 6.1 마이페이지 리디자인

```
┌────────────────────────────────────────────────────┐
│  마이페이지                                         │
├────────────────────────────────────────────────────┤
│                                                    │
│  ┌──── 프로필 섹션 ────────────────────────────┐   │
│  │  [이니셜 아바타]  user@email.com             │   │
│  │                   가입일: 2026.04.10         │   │
│  └──────────────────────────────────────────────┘   │
│                                                    │
│  ┌──── 내 북마크 ──────────────────────────────┐   │
│  │  ┌─────────┐ ┌─────────┐ ┌─────────┐       │   │
│  │  │  AAPL   │ │  MSFT   │ │  NVDA   │       │   │
│  │  │ $198.50 │ │ $425.30 │ │ $880.20 │       │   │
│  │  │ +1.25%  │ │ -0.48%  │ │ +3.12%  │       │   │
│  │  │ [삭제]  │ │ [삭제]  │ │ [삭제]  │       │   │
│  │  └─────────┘ └─────────┘ └─────────┘       │   │
│  └──────────────────────────────────────────────┘   │
│                                                    │
│  ��──── 알림 설정 ──────────────────────────────┐   │
│  │  [글로벌 푸시 알림]  ──── [ON/OFF 토글]      │   │
│  │                                              │   │
│  │  AAPL  가격±5% [v] 뉴스 [v] 시그널 [v]      │   │
│  │  MSFT  가격±3% [v] 뉴스 [ ] 시그널 [v]      │   │
│  └──────────────────────────────────────────────┘   │
│                                                    │
│  ┌──── 계정 ───────────────────────────────────┐   │
│  │  [로그아웃]            [계정 삭제]           │   │
��  └──────────────────────────────────────────────┘   │
└────────────────────────────────────────────────────┘
```

**컴포넌트 분해:**

| Component | File | Responsibility |
|-----------|------|----------------|
| `MyPage` | `app/my/page.tsx` | 페이지 컨테이너 (auth guard) |
| `ProfileSection` | `features/my-page/profile-section.tsx` | ���바타 + 이메일 + 가입일 |
| `BookmarkGrid` | `features/my-page/bookmark-grid.tsx` | 카드 그리드 레이아웃 |
| `BookmarkCard` | `features/my-page/bookmark-card.tsx` | 개별 북마크 카드 (가격/변동률) |
| `NotificationSection` | `features/my-page/notification-section.tsx` | 글로벌 토글 + 종목별 설정 |
| `AccountSection` | `features/my-page/account-section.tsx` | 로그아웃 + 계정 삭제 |

### 6.2 종목 상세 알림 설정

```
StockHeader 기존:
  [AAPL] [NYSE] [★ 북마크]

변경 후:
  [AAPL] [NYSE] [★ 북마크] [🔔 알림 설정]

알림 설정 클릭 시 모달:
┌─────────────────────────────────┐
│  AAPL 알림 설정                  │
│                                 │
│  가격 변동 임계치: [±5%] ▼       │
│  새 뉴스 발생 시:  [ON]         │
│  AI 시그널 변화:   [ON]         │
│                                 │
│  [저장]          [취소]          │
└─────────────────────────────────┘
```

**컴포넌트:**

| Component | File | Responsibility |
|-----------|------|----------------|
| `NotificationButton` | `features/stock-detail/notification-button.tsx` | 종목 상세 헤더 알림 버튼 |
| `NotificationSettingModal` | `features/stock-detail/notification-setting-modal.tsx` | 알림 설정 모달 |

**동��:**
1. 비로그인 → `AuthGuardModal` 표시 (기존 패턴)
2. 미북마크 종목 → 알림 저장 시 자동 `addBookmark()` 호출 후 `upsertNotificationSetting()`
3. 기북마크 종목 → 기존 설정 로드 후 수정

### 6.3 차트 개선

`ChartPanel` 변경 사항:
- 거래량 서브차��� 추가 (히스토그램)
- `timeScale.timeVisible` — D1일 때만 true (분봉 시각 표시)
- 1W: 일봉 5개로 변경되므로 캔들 표시 자연스러움
- 5Y: 주봉으로 변경되므로 260개 캔들 → 적절한 밀도

```typescript
// chart-panel.tsx 변경 핵심
useEffect(() => {
  // ... 기존 chart 생성 후
  const volumeSeries = chart.addHistogramSeries({
    priceFormat: { type: 'volume' },
    priceScaleId: 'volume',
  });
  chart.priceScale('volume').applyOptions({
    scaleMargins: { top: 0.8, bottom: 0 },
  });
  volumeSeriesRef.current = volumeSeries;
}, []);

useEffect(() => {
  if (!data) return;
  // 캔들 데이터
  seriesRef.current?.setData(data.map(c => ({
    time: c.time as UTCTimestamp,
    open: c.open, high: c.high, low: c.low, close: c.close,
  })));
  // 거래량 데이터
  volumeSeriesRef.current?.setData(data.map(c => ({
    time: c.time as UTCTimestamp,
    value: c.volume,
    color: c.close >= c.open ? '#16a34a80' : '#dc262680',
  })));
}, [data]);
```

---

## 7. Residual Gap Fixes

### 7.1 SearchHit.exchange nullable (Phase 1 gap)

**현재 상태**: `SearchHit` record에 `exchange` 필드가 이미 nullable로 선언됨.
**FE 문제**: `stock.ts`의 `SearchHit.exchange: string` — nullable이 아님.

```typescript
// types/stock.ts 변경
export interface SearchHit {
  ticker: string;
  name: string;
  exchange: string | null;  // nullable 반영
  matchType: string;
}
```

### 7.2 Quote.volume (Twelve Data 연동)

**현재**: TwelveData `/quote` 응답의 `TwelveQuoteResponse`에 volume 필드 누락 → Quote에 `0L` 전달.

```java
// TwelveDataClient.TwelveQuoteResponse에 volume 추가
record TwelveQuoteResponse(
    // ... 기존 필드
    Long volume  // 추가
) {}

// quote() 메서드에서 volume 전달
return new Quote(
    symbol, resp.close(), change, pctChange,
    resp.high(), resp.low(), resp.open(), prev,
    resp.volume() != null ? resp.volume() : 0L,  // 변경
    OffsetDateTime.ofInstant(...)
);
```

### 7.3 MarketMover.volume (Phase 3 gap)

**현재**: `MarketMoversService.toMover()`에서 `0L` 하드코딩.
**원인**: FMP gainers/losers API가 volume 미포함.

**해결**: FMP API 개별 quote 호출은 비용 대비 효과 낮음. 대신 FE에서 volume이 0일 때 숨김 처리.

```typescript
// market-movers.tsx
{m.volume > 0 && (
  <span className="text-xs text-gray-500">{formatVolume(m.volume)}</span>
)}
```

### 7.4 usdKrwChange (Phase 3 gap)

**현재 분석**: `MarketOverviewService.fetchUsdKrw()`에서 `q.change()` 반환 중.
Finnhub `USDKRW=X` quote가 change를 제공하면 이미 동작함.

**확인 필요**: 실제 API 응답에서 change가 null인지 검증. null이면 `previousClose` 기반 수동 계산.

```java
// fetchUsdKrw() 보완
BigDecimal change = q.change();
if (change == null || change.signum() == 0) {
    // 전일종가 기반 수동 계산
    if (q.previousClose() != null && q.previousClose().signum() > 0) {
        change = q.price().subtract(q.previousClose());
    }
}
return new BigDecimal[]{q.price(), change};
```

---

## 8. Rate Limiter (Token Bucket)

### 8.1 설계

외부 라이브러리(Bucket4j) 없이 `ConcurrentHashMap` + `AtomicLong` 기반 Token Bucket 자체 구현.

```java
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    // IP 기반 Token Bucket: 분당 60 요청 (무료 서비스 적정 수준)
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final long capacity;          // default 60
    private final long refillPerMinute;   // default 60

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                     HttpServletResponse resp,
                                     FilterChain chain) {
        String clientIp = extractClientIp(req);
        TokenBucket bucket = buckets.computeIfAbsent(clientIp, k -> new TokenBucket(capacity, refillPerMinute));

        if (bucket.tryConsume()) {
            chain.doFilter(req, resp);
        } else {
            resp.setStatus(429);
            resp.setContentType("application/json");
            resp.getWriter().write(
                "{\"error\":{\"code\":\"RATE_LIMITED\",\"message\":\"요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.\"}}"
            );
        }
    }

    // TokenBucket 내부 클래스: AtomicLong CAS 기반 thread-safe
    static class TokenBucket {
        private final long capacity;
        private final double refillRatePerMs;
        private final AtomicLong tokens;
        private volatile long lastRefillTime;
        // tryConsume(), refill() ...
    }
}
```

### 8.2 Security Chain 통합

```java
// SecurityConfig — public chain에 RateLimitFilter 추가
http.addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class);
```

---

## 9. Indicator QA (기술지표 검증)

### 9.1 검증 방법

1. DB에 적재된 AAPL/TSLA/MSFT 1Y 일봉으로 지표 계산
2. 동일 날짜의 TradingView RSI(14), MACD(12,26,9), BB(20,2) 값과 비교
3. 허용 오차: ±0.5% (소수점 반올림 차이)

### 9.2 검증 항목

| 지표 | 파라미터 | 검증 종목 | 비교 대상 |
|------|----------|----------|-----------|
| RSI | 14 | AAPL, TSLA, MSFT | TradingView RSI |
| MACD | 12, 26, 9 | AAPL, TSLA, MSFT | TradingView MACD |
| Bollinger | 20, 2.0 | AAPL, TSLA, MSFT | TradingView BB |

### 9.3 잠재적 불일치 원인

- **데이터 범위**: ta4j warm-up 기간 포함 여부
- **Adjusted vs Raw close**: ta4j에 adj_close를 넣어야 TradingView와 일치
- **EMA 시작점**: ta4j vs TradingView의 초기 EMA 계산 차이
- **정밀도**: BigDecimal vs double 변환 시 오차

---

## 10. Implementation Order

| Step | Scope | Files | Depends On | FR |
|------|-------|-------|------------|-----|
| **1** | Flyway V8 `candles` 테이블 | 1 | — | FR-01 |
| **2** | `CandleEntity` + `CandleId` + `CandleRepository` | 3 | Step 1 | FR-01 |
| **3** | `YahooFinanceClient` | 1 | — | FR-02, FR-04 |
| **4** | `CandleService` 리팩터 (DB-first + fallback + 주봉 집계) | 1 | Step 2, 3 | FR-03, FR-04, FR-06, FR-08 |
| **5** | `TimeFrame` enum 변경 (dbBacked 필드) | 1 | — | FR-07, FR-08 |
| ~~6~~ | ~~벌크 초기 로드~~ (삭제: on-demand 방식 채택) | — | — | — |
| **7** | `CandleBatchScheduler` 일간 배치 | 1 | Step 2, 3 | FR-05 |
| **8** | `ChartPanel` 거래량 서브차트 + timeVisible | 1 | Step 4 | FR-08 |
| **9** | 마이페이지 리디자인 (6 컴포넌트) | 7 | — | FR-09 |
| **10** | `NotificationButton` + `NotificationSettingModal` | 2 | — | FR-10, FR-11 |
| **11** | 잔여 gap: SearchHit, Quote.volume, MarketMover, usdKrwChange | 5 | — | FR-12~15 |
| **12** | `RateLimitFilter` (Bucket4j) | 2 | — | FR-16 |
| **13** | 기술지표 검증 + 수정 | 2 | Step 4 | FR-17 |
| **14** | Auth design 문서 동기화 | 1 | — | FR-18 |

**총 예상**: ~33 파일, ~2,500+ lines

---

## 11. Test Plan

### 11.1 Test Scope

| Type | Target | Tool |
|------|--------|------|
| Unit | CandleService DB/fallback 분기 | JUnit 5 + Mockito |
| Unit | YahooFinanceClient JSON 파싱 | MockWebServer |
| Unit | 주봉 집계 로직 | JUnit 5 |
| Unit | RateLimitFilter | MockMvc |
| Integration | candles 테이블 CRUD | @DataJpaTest |
| Integration | CandleBatchScheduler | @SpringBootTest |
| FE | 마이페이지 컴포넌트 렌더링 | Vitest (smoke) |

### 11.2 Key Test Cases

- [ ] DB에 데이터 있을 때 → API 호출 없이 DB 결과 반환
- [ ] DB에 데이터 없을 때 → Yahoo API 호출 + DB persist + 결과 반환
- [ ] 5Y 타임프레임 → 주봉 집계 정확성 (open=첫봉, close=마지막봉, high=max, low=min)
- [ ] RateLimitFilter 60req/min 초과 시 429 반환
- [ ] 미북마크 종목 알림 설정 시 자동 북마크 추가

---

## 12. Auth 구현 현황 (설계 문서 동기화)

Phase 4 Auth 설계(`docs/archive/2026-04/auth/auth.design.md`)와 실제 구현 간 차이를 기록합니다.

| 항목 | 설계 (Phase 4) | 실제 구현 | 비고 |
|------|---------------|----------|------|
| SecurityFilterChain | 단일 체인 (`filterChain`) | **two-chain** — `protectedFilterChain` (Order 1) + `publicFilterChain` (Order 2) | 인증 필요 API와 공개 API를 명확히 분리 |
| JWT 서명 알고리즘 | RS256 만 명시 | **ES256 + RS256 병행** (`jwsAlgorithms` 에 둘 다 등록) | Supabase Auth v2가 ES256(ECDSA P-256) 사용 |
| 보호 대상 API | 미상세 | `securityMatcher("/api/v1/me", "/api/v1/bookmarks/**", "/api/v1/push/*", "/api/v1/notifications/**", "/api/v1/stocks/*/ai-signal")` | 명시적 경로 매칭 |
| Rate Limiter | 미포함 | public chain에 `RateLimitFilter` 적용 (Token Bucket, 60 req/min) | Phase 4.5에서 추가 |
| CORS | `CorsConfigurationSource` 빈 | 양쪽 체인 모두 `.cors(Customizer.withDefaults())` | `WebCorsConfig` 빈 재사용 |

> 아카이브 문서는 읽기 전용 정책이므로 원본은 수정하지 않고, 이 섹션에서 실제 구현 차이를 보완합니다.

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 0.1 | 2026-04-17 | Initial draft | Claude + wonseok-han |
| 0.2 | 2026-04-17 | 벌크 시드 제거 → on-demand 방식, Bucket4j → Token Bucket 자체 구현, Auth 현황 동기화 | Claude + wonseok-han |
