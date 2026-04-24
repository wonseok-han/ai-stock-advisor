# api-cache-optimization Design Document

> **Summary**: MarketStatusResolver 기반 적응형 캐시 TTL + FMP 원본 캐싱으로 외부 API 호출 60~80% 감소
>
> **Project**: 지금이니?! (Nowini)
> **Version**: v0.1.0
> **Author**: Claude
> **Date**: 2026-04-23
> **Status**: Draft
> **Planning Doc**: [api-cache-optimization.plan.md](../../01-plan/features/api-cache-optimization.plan.md)

## Executive Summary

| 관점 | 요약 |
|------|------|
| **Problem** | 외부 API 무료 할당량(FMP 250/일, Finnhub 60/분)이 1인 개발·테스트만으로도 한계에 도달. 장외시간(17.5시간+주말)에도 동일한 짧은 TTL로 불필요한 호출 발생 |
| **Solution** | `MarketStatusResolver.durationUntilNextOpen()` 기반 적응형 TTL — 장 상태(OPEN/CLOSED)에 따라 캐시 TTL을 동적 조절하고, FMP 원본 응답을 Redis에 통합 캐싱 |
| **Function UX Effect** | 사용자 체감 변화 없음 (동일한 데이터, 동일한 응답 속도). 장외시간 API 에러/지연 감소로 안정성 향상 |
| **Core Value** | API 일일 호출량 60~80% 감소 → 무료 플랜 지속 가능성 확보 + 서비스 안정성 향상 |

---

## 1. Overview

### 1.1 Design Goals

1. 장 상태(OPEN/CLOSED)에 따라 캐시 TTL을 자동 조절하여 장외시간 불필요한 API 호출 제거
2. FMP 원본 응답을 Redis에 통합 캐싱하여 일일 250회 한도 내 안정적 운영
3. 기존 `RedisCacheAdapter.getOrLoad()` 패턴을 유지하면서 최소 변경으로 적용

### 1.2 Design Principles

- **최소 침습**: 기존 `getOrLoad()` 시그니처·패턴 유지, TTL 결정 로직만 서비스 내부에서 분기
- **단일 책임**: TTL 계산은 `MarketStatusResolver`에, 캐시 연산은 `RedisCacheAdapter`에 집중
- **점진적 적용**: 서비스별 독립 적용 가능, 전체 일괄 배포 불필요

---

## 2. Architecture

### 2.1 Component Diagram

```
┌──────────────┐     ┌──────────────────────────┐     ┌──────────────┐
│  Controller  │────▶│       Service Layer       │────▶│ External API │
│  (REST)      │     │ (QuoteService, etc.)      │     │ (FMP, Yahoo, │
└──────────────┘     │                           │     │  Finnhub)    │
                     │  ┌─────────────────────┐  │     └──────────────┘
                     │  │ MarketStatusResolver │  │
                     │  │ ├ resolve() → OPEN   │  │
                     │  │ │              CLOSED │  │
                     │  │ └ durationUntilNext  │  │
                     │  │   Open() → Duration  │  │
                     │  └─────────────────────┘  │
                     │           │                │
                     │  TTL = isOpen ? shortTTL   │
                     │           : longTTL        │
                     │           ▼                │
                     │  ┌─────────────────────┐  │
                     │  │  RedisCacheAdapter   │  │──▶ Upstash Redis
                     │  │  getOrLoad(key,type, │  │
                     │  │    ttl, loader)      │  │
                     │  └─────────────────────┘  │
                     └──────────────────────────┘
```

### 2.2 Data Flow

```
요청 → Service
  ├─ MarketStatusResolver.resolve() → OPEN / CLOSED
  ├─ CLOSED? → durationUntilNextOpen() 또는 고정 장외 TTL 계산
  ├─ TTL 결정 (장중: 기존 값, 장외: 연장된 값)
  └─ cache.getOrLoad(key, type, ttl, loader)
       ├─ 캐시 HIT → 즉시 반환
       └─ 캐시 MISS → loader(외부 API 호출) → Redis 저장 → 반환
```

### 2.3 Dependencies

| Component | Depends On | Purpose |
|-----------|-----------|---------|
| 7개 서비스 (Quote, Overview, ...) | MarketStatusResolver | 장 상태 판별 + TTL 계산 |
| 7개 서비스 | RedisCacheAdapter | 캐시 저장/조회 (기존과 동일) |
| MarketStatusResolver | java.time (JDK) | ET 시간대 계산 |
| FmpClient | WebClient | FMP API 호출 (기존과 동일) |

---

## 3. Core Design: 적응형 TTL

### 3.1 `MarketStatusResolver.durationUntilNextOpen()` 메서드

기존 `MarketStatusResolver`에 신규 static 메서드 1개 추가.

```java
// MarketStatusResolver.java
public static Duration durationUntilNextOpen() {
    ZonedDateTime now = ZonedDateTime.now(ET);
    MarketStatus status = resolve();

    if (status == MarketStatus.OPEN) {
        return Duration.ZERO;
    }

    ZonedDateTime nextOpen = nextOpenTime(now);
    return Duration.between(now, nextOpen);
}

private static ZonedDateTime nextOpenTime(ZonedDateTime now) {
    ZonedDateTime candidate = now.toLocalDate().atTime(OPEN).atZone(ET);

    // 오늘 개장 시각이 아직 오지 않았고 평일이면 → 오늘 개장
    if (now.isBefore(candidate) && isWeekday(now.getDayOfWeek())) {
        return candidate;
    }

    // 그 외 → 다음 평일 09:30 ET
    candidate = candidate.plusDays(1);
    while (!isWeekday(candidate.getDayOfWeek())) {
        candidate = candidate.plusDays(1);
    }
    return candidate;
}

private static boolean isWeekday(DayOfWeek dow) {
    return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY;
}
```

**결정 매트릭스:**

| 시나리오 | now (ET) | 반환값 |
|---------|---------|--------|
| 정규장 중 (월~금 09:30~16:00) | 수 14:00 | `Duration.ZERO` |
| 평일 장 마감 후 | 수 18:00 | ~15.5시간 (목 09:30까지) |
| 평일 장 개시 전 | 목 07:00 | ~2.5시간 (목 09:30까지) |
| 금요일 장 마감 후 | 금 18:00 | ~63.5시간 (월 09:30까지) |
| 토요일 | 토 12:00 | ~45.5시간 (월 09:30까지) |
| 일요일 | 일 12:00 | ~21.5시간 (월 09:30까지) |

### 3.2 서비스별 적응형 TTL 전략

각 서비스에서 TTL을 결정하는 로직. `RedisCacheAdapter`는 변경하지 않고, 서비스가 전달하는 `Duration ttl` 값만 분기.

| 서비스 | 캐시 키 | 장중 TTL | 장외 TTL | 전략 |
|--------|---------|:--------:|:--------:|------|
| `QuoteService` | `quote:{ticker}` | 30초 | `durationUntilNextOpen()` | 다음 개장까지 |
| `MarketOverviewService` | `market:overview` | 5분 | `durationUntilNextOpen()` | 다음 개장까지 |
| `MarketMoversService` | `market:movers` | 15분 | `durationUntilNextOpen()` | 다음 개장까지 |
| `SectorPerformanceService` | `market:sectors` | 15분 | `durationUntilNextOpen()` | 다음 개장까지 |
| `CandleService` | `candle:{ticker}:{tf}` | 5분 | `durationUntilNextOpen()` | 다음 개장까지 |
| `IndicatorService` | `ind:{ticker}` | 5분 | `durationUntilNextOpen()` | 다음 개장까지 |
| `MarketNewsService` | `market:news` | 15분 | 30분 | 고정 장외 TTL (뉴스는 장외에도 발행됨) |

**TTL 미변경 서비스 (이미 24시간 이상):**

| 서비스 | 캐시 키 | TTL | 이유 |
|--------|---------|:---:|------|
| `StockProfileService` | `profile:{ticker}` | 24시간 | 기업 프로필 변동 없음 |
| `CompanyOverviewService` | `overview:{ticker}` | 24시간 | 재무 지표 일 1회 수준 |
| `AnalystEstimatesService` | `analyst:{ticker}` | 24시간 | 애널리스트 데이터 저빈도 |
| `YahooFinanceClient` | `yahoo:summary:{ticker}` | 24시간 | 원본 통합 캐시 |
| `AiSignalService` | `ai:{ticker}:v*` | 설정값(기본 60분) | AI 분석은 장 상태 무관 |
| `SignalAccuracyService` | `ai:accuracy:*` | 1시간 | 정합도는 장 상태 무관 |

### 3.3 서비스 내 TTL 결정 패턴

모든 서비스에 단일 패턴 적용. 별도 유틸리티 클래스 없이 서비스 내부에서 직접 분기.

#### 통합 패턴: 다음 개장까지 TTL (전 서비스 공통)

```java
// QuoteService.java (예시)
private static final Duration TTL_OPEN = Duration.ofSeconds(30);

public Quote getQuote(String ticker) {
    Duration ttl = MarketStatusResolver.resolve() == MarketStatus.OPEN
            ? TTL_OPEN : MarketStatusResolver.durationUntilNextOpen();
    return cache.getOrLoad("quote:" + ticker, TYPE, ttl, () -> fetch(ticker));
}
```

> **설계 결정**: 장외시간에는 시세·지표·뉴스 모두 변동이 없으므로 고정 TTL(4h, 1h, 30m) 대신 `durationUntilNextOpen()`으로 통일. 한 번 캐싱하면 다음 개장까지 유지하여 불필요한 외부 API 호출을 원천 차단.
>
> **안전장치**: `durationUntilNextOpen()`이 `Duration.ZERO`를 반환하면(정규장 중) 기존 `TTL_OPEN`을 사용. 금요일 장 마감 후 최대 ~63.5시간이지만, Redis Upstash 무료 플랜은 메모리 기반이므로 장기 TTL에 문제 없음.

---

## 4. Client 레벨 원본 캐싱

### 4.1 설계 원칙

모든 외부 API 클라이언트에 `RedisCacheAdapter`를 주입하여, **외부 API 원본 응답을 Redis에 캐싱**.
서비스 레벨 캐시(가공 결과)와 함께 2중 보호 구조를 형성.

```
웹 → Service → Client (Redis 확인 → miss시 외부 API → Redis 저장) → Service (가공) → 웹
```

### 4.2 Client별 캐시 키 목록

#### FmpClient (8개 엔드포인트)

| Redis 키 | 엔드포인트 | 장중 TTL | 장외 TTL |
|----------|----------|:--------:|:--------:|
| `fmp:gainers` | `/biggest-gainers` | 15분 | 다음 개장까지 |
| `fmp:losers` | `/biggest-losers` | 15분 | 다음 개장까지 |
| `fmp:sectors` | `/sector-performance` | 15분 | 다음 개장까지 |
| `fmp:profile:{ticker}` | `/profile` | 24시간 | 24시간 |
| `fmp:ratios:{ticker}` | `/ratios-ttm` | 24시간 | 24시간 |
| `fmp:grades:{ticker}` | `/grades-consensus` | 24시간 | 24시간 |
| `fmp:price-target:{ticker}` | `/price-target-consensus` | 24시간 | 24시간 |
| `fmp:estimates:{ticker}` | `/analyst-estimates` | 24시간 | 24시간 |

#### FinnhubClient (2개)

| Redis 키 | 엔드포인트 | 장중 TTL | 장외 TTL |
|----------|----------|:--------:|:--------:|
| `finnhub:quote:{ticker}` | `/quote` | 30초 | 다음 개장까지 |
| `finnhub:profile:{ticker}` | `/stock/profile2` | 24시간 | 24시간 |

#### FinnhubMarketNewsClient (1개)

| Redis 키 | 엔드포인트 | 장중 TTL | 장외 TTL |
|----------|----------|:--------:|:--------:|
| `finnhub:news:general` | `/news?category=general` | 15분 | 30분 |

#### TwelveDataClient (2개)

| Redis 키 | 엔드포인트 | 장중 TTL | 장외 TTL |
|----------|----------|:--------:|:--------:|
| `twelve:quote:{symbol}` | `/quote` | 30초 | 다음 개장까지 |
| `twelve:series:{symbol}:{interval}` | `/time_series` | 5분 | 다음 개장까지 |

#### YahooFinanceClient (3개 — 기존 1개 + 신규 2개)

| Redis 키 | 엔드포인트 | 장중 TTL | 장외 TTL |
|----------|----------|:--------:|:--------:|
| `yahoo:chart:{ticker}` | `/v8/finance/chart` (1m) | 30초 | 다음 개장까지 |
| `yahoo:intraday:{ticker}` | `/v8/finance/chart` (5m) | 5분 | 다음 개장까지 |
| `yahoo:summary:{ticker}` | `/v10/finance/quoteSummary` | 24시간 | 24시간 |

### 4.3 2중 캐시 구조

| 레이어 | 캐시 대상 | 목적 |
|--------|----------|------|
| **Client** | 외부 API 원본 응답 | 외부 API 호출 자체를 방지 |
| **Service** | 가공된 결과 | 재가공(변환, 번역, 계산) 방지 |

Service 캐시 만료 → Client 캐시 여전히 유효 → 외부 API 호출 없이 재가공만 수행.

---

## 5. 상세 변경 명세

### 5.1 파일 변경 목록

| # | 파일 | 변경 유형 | 설명 |
|---|------|:--------:|------|
| 1 | `MarketStatusResolver.java` | 수정 | `durationUntilNextOpen()`, `resolve(ZonedDateTime)` 오버로드 추가 |
| 2 | `QuoteService.java` | 수정 | 서비스 레벨 적응형 TTL (30s→다음 개장) |
| 3 | `MarketOverviewService.java` | 수정 | 서비스 레벨 적응형 TTL (5m→다음 개장) |
| 4 | `MarketMoversService.java` | 수정 | 서비스 레벨 적응형 TTL (15m→다음 개장) |
| 5 | `SectorPerformanceService.java` | 수정 | 서비스 레벨 적응형 TTL (15m→다음 개장) |
| 6 | `CandleService.java` | 수정 | 서비스 레벨 적응형 TTL (5m→다음 개장, 인트라데이만) |
| 7 | `IndicatorService.java` | 수정 | 서비스 레벨 적응형 TTL (5m→다음 개장) |
| 8 | `MarketNewsService.java` | 수정 | 서비스 레벨 적응형 TTL (15m→30m, 뉴스 고정) |
| 9 | `FmpClient.java` | 수정 | Client 레벨 원본 캐싱 (8개 엔드포인트) |
| 10 | `FinnhubClient.java` | 수정 | Client 레벨 원본 캐싱 (quote, profile) |
| 11 | `FinnhubMarketNewsClient.java` | 수정 | Client 레벨 원본 캐싱 (news) |
| 12 | `TwelveDataClient.java` | 수정 | Client 레벨 원본 캐싱 (quote, timeSeries) |
| 13 | `YahooFinanceClient.java` | 수정 | Client 레벨 원본 캐싱 (chart, intraday) |
| 14 | `MarketStatusResolverTest.java` | 수정 | `durationUntilNextOpen()` 테스트 M8~M13 추가 |
| **합계** | | | **14개 파일** |

### 5.2 변경 상세

#### 5.2.1 `MarketStatusResolver.java`

**경로**: `apps/api/src/main/java/com/aistockadvisor/stock/domain/MarketStatusResolver.java`

**추가 메서드:**

```java
/**
 * 다음 정규장 개장(09:30 ET)까지 남은 Duration 반환.
 * OPEN 상태 → Duration.ZERO.
 */
public static Duration durationUntilNextOpen() {
    ZonedDateTime now = ZonedDateTime.now(ET);
    if (resolve() == MarketStatus.OPEN) {
        return Duration.ZERO;
    }
    return Duration.between(now, nextOpenTime(now));
}

private static ZonedDateTime nextOpenTime(ZonedDateTime now) {
    ZonedDateTime today930 = now.toLocalDate().atTime(OPEN).atZone(ET);

    if (now.isBefore(today930) && isWeekday(now.getDayOfWeek())) {
        return today930;
    }

    ZonedDateTime next = today930.plusDays(1);
    while (!isWeekday(next.getDayOfWeek())) {
        next = next.plusDays(1);
    }
    return next;
}

private static boolean isWeekday(DayOfWeek dow) {
    return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY;
}
```

**변경 영향**: 기존 메서드(`resolve()`, `priceLabel()`, `resolveByPeriod()`)는 수정 없음. 새 메서드 3개 추가만.

#### 5.2.2~5.2.8 서비스 레벨 적응형 TTL (전 서비스 공통 패턴)

모든 서비스가 동일한 패턴을 사용합니다. `TTL_CLOSED` 상수 없이 `durationUntilNextOpen()` 직접 호출:

```java
private static final Duration TTL_OPEN = Duration.ofSeconds(30); // 서비스마다 다름

Duration ttl = MarketStatusResolver.resolve() == MarketStatus.OPEN
        ? TTL_OPEN : MarketStatusResolver.durationUntilNextOpen();
cache.getOrLoad(key, TYPE, ttl, loader);
```

**적용 서비스 목록:**

| 서비스 | 장중 TTL | 비고 |
|--------|:--------:|------|
| `QuoteService` | 30초 | |
| `MarketOverviewService` | 5분 | |
| `MarketMoversService` | 15분 | |
| `SectorPerformanceService` | 15분 | |
| `CandleService` | 5분 | 인트라데이만, 일봉은 DB-first |
| `IndicatorService` | 5분 | |
| `MarketNewsService` | 15분 (장중) / 30분 (장외) | 뉴스는 장외에도 발행되므로 고정 TTL |

---

## 6. Error Handling

### 6.1 `durationUntilNextOpen()` 안전성

| 상황 | 동작 | 이유 |
|------|------|------|
| 정규장 중 호출 | `Duration.ZERO` 반환 | 서비스에서 `TTL_OPEN` 사용하므로 영향 없음 |
| 주말 호출 | 월요일 09:30 ET까지 Duration | 최대 ~63.5시간, Redis TTL 정상 범위 |
| `resolve()` 실패 | 불가 — 순수 시간 계산, 외부 의존성 없음 | JDK `java.time` API만 사용 |

### 6.2 Redis TTL 상한

Redis는 TTL에 상한이 없으므로 (이론상 수년도 가능), 주말 63시간도 문제 없음. Upstash 무료 플랜은 256MB 메모리 한도만 존재하며, TTL 기반 키 관리로 메모리 사용은 감소.

### 6.3 기존 에러 처리 유지

`RedisCacheAdapter`의 fail-open 패턴(Redis 장애 시 `loader` 호출)은 변경 없음. 적응형 TTL은 "캐시에 저장할 때의 만료 시간"만 변경하므로, Redis 장애 시 기존과 동일하게 외부 API를 직접 호출.

---

## 7. Security Considerations

- [x] 외부 API 키 노출 없음 (기존과 동일, 서버 사이드에서만 사용)
- [x] Redis 접근은 Upstash REST TLS로 암호화 (기존과 동일)
- [x] 캐시 데이터에 개인정보 없음 (시세, 지표, 뉴스만)
- [x] Rate limiting 완화로 429 에러 감소 → 서비스 안정성 향상

---

## 8. Test Plan

### 8.1 Test Scope

| 유형 | 대상 | 도구 |
|------|------|------|
| Unit Test | `MarketStatusResolver.durationUntilNextOpen()` | JUnit 5 + AssertJ |
| Integration | 서비스별 TTL 분기 동작 | 브라우저 + Redis TTL 확인 |

### 8.2 `MarketStatusResolverTest` 추가 케이스

기존 M1~M7에 이어서 M8~M13 추가:

| ID | 시나리오 | 검증 |
|----|---------|------|
| M8 | `durationUntilNextOpen()` — 정규장 중 | `Duration.ZERO` |
| M9 | `durationUntilNextOpen()` — 평일 장 마감 후 (수 18:00 ET) | > 0, < 16시간 |
| M10 | `durationUntilNextOpen()` — 평일 장 개시 전 (목 07:00 ET) | > 0, ≤ 2.5시간 |
| M11 | `durationUntilNextOpen()` — 금요일 장 마감 후 (금 18:00 ET) | > 60시간 (월 09:30까지) |
| M12 | `durationUntilNextOpen()` — 토요일 | > 0, < 50시간 |
| M13 | `durationUntilNextOpen()` — 일요일 | > 0, < 22시간 |

> `durationUntilNextOpen()`이 `static` 메서드이고 `ZonedDateTime.now()`를 내부에서 호출하기 때문에 시간 주입이 불가. **테스트 가능성을 위해** `durationUntilNextOpen(ZonedDateTime now)` 오버로드를 추가하여, 테스트에서 특정 시각을 주입할 수 있도록 함.

```java
// 공개: 현재 시각 기반 (프로덕션 사용)
public static Duration durationUntilNextOpen() {
    return durationUntilNextOpen(ZonedDateTime.now(ET));
}

// 패키지 접근: 테스트용 시각 주입
static Duration durationUntilNextOpen(ZonedDateTime now) {
    if (resolve(now) == MarketStatus.OPEN) {
        return Duration.ZERO;
    }
    return Duration.between(now, nextOpenTime(now));
}

// resolve()도 시각 주입 오버로드 (테스트용)
static MarketStatus resolve(ZonedDateTime now) {
    DayOfWeek dow = now.getDayOfWeek();
    if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
        return MarketStatus.CLOSED;
    }
    LocalTime t = now.toLocalTime();
    return (t.compareTo(OPEN) >= 0 && t.compareTo(CLOSE) < 0)
            ? MarketStatus.OPEN : MarketStatus.CLOSED;
}
```

### 8.3 통합 검증

| # | 검증 항목 | 방법 |
|---|----------|------|
| 1 | 장외시간에 `market:movers` Redis TTL 확인 | `redis-cli TTL market:movers` 또는 Upstash 콘솔 |
| 2 | 장외시간에 `quote:AAPL` TTL이 다음 개장까지 | Redis TTL 확인 |
| 3 | 정규장 시간에 기존 TTL과 동일 | BE 로그 + Redis TTL |
| 4 | `make api-check` 통과 | CI 등가 |
| 5 | 브라우저에서 기존 기능 regression 없음 | 수동 검증 |

---

## 9. Clean Architecture

### 9.1 Layer Structure

| Layer | 구성요소 | 위치 |
|-------|---------|------|
| **Domain** | `MarketStatusResolver`, `MarketStatus` | `stock/domain/` |
| **Application** | 7개 Service (Quote, Overview, ...) | `stock/service/`, `market/service/` |
| **Infrastructure** | `RedisCacheAdapter`, `FmpClient` | `cache/`, `market/infra/` |

### 9.2 Dependency Rules

```
Controller → Service → Domain (MarketStatusResolver)
                    → Infrastructure (RedisCacheAdapter, FmpClient)
```

- `MarketStatusResolver`는 **Domain 레이어** — 외부 의존성 없음 (JDK `java.time`만)
- Service가 Domain과 Infrastructure를 조합 — 기존 의존 방향 유지
- `FmpClient`는 순수 HTTP 어댑터로 유지 (캐시 로직 추가 안 함)

---

## 10. Coding Convention Reference

### 10.1 BE 컨벤션 준수

| 항목 | 적용 |
|------|------|
| 클래스명 PascalCase | `MarketStatusResolver` (기존) |
| 메서드명 camelCase | `durationUntilNextOpen()` |
| 상수 UPPER_SNAKE_CASE | `TTL_OPEN`, `TTL_CLOSED`, `TTL_INTRADAY_OPEN` |
| 패키지 lowercase.dot | `com.aistockadvisor.stock.domain` (기존) |

### 10.2 상수 명명 규칙

기존 서비스의 `TTL`, `CACHE_TTL` 단일 상수를 `TTL_OPEN` 하나로 변경. 장외 TTL은 `MarketStatusResolver.durationUntilNextOpen()`을 직접 호출하므로 상수 불필요.

```
기존: private static final Duration TTL = Duration.ofSeconds(30);
변경: private static final Duration TTL_OPEN = Duration.ofSeconds(30);
      // 장외는 MarketStatusResolver.durationUntilNextOpen() 직접 호출
```

---

## 11. Implementation Guide

### 11.1 구현 순서

1. [ ] **Step 1**: `MarketStatusResolver` 확장 — `durationUntilNextOpen()` + 테스트용 오버로드 + 테스트 M8~M13
2. [ ] **Step 2**: FMP 관련 서비스 적응형 TTL — `MarketMoversService`, `SectorPerformanceService` (패턴 B)
3. [ ] **Step 3**: 나머지 서비스 적응형 TTL — `QuoteService`, `MarketOverviewService`, `CandleService`, `IndicatorService`, `MarketNewsService` (패턴 A)
4. [ ] **Step 4**: `make api-check` + 브라우저 검증

### 11.2 예상 API 호출량 효과

| API | 현재 추정 (일) | 적용 후 추정 (일) | 감소율 |
|-----|:------------:|:---------------:|:-----:|
| **FMP** | ~288 | ~78 | **-73%** |
| **Finnhub** (quote) | ~384 | ~96 | **-75%** |
| **Finnhub** (news) | ~96 | ~70 | **-27%** |
| **Yahoo** (candle) | ~168 | ~78 | **-54%** |
| **TwelveData** | 매우 낮음 | 매우 낮음 | - |

> 계산 근거: 장중 6.5시간만 기존 TTL로 호출, 장외 17.5시간은 캐시 재사용.

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 0.1 | 2026-04-23 | Initial draft | Claude |
