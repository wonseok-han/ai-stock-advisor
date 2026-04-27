# yahoo-migration Planning Document

> **Summary**: TwelveData API의 일상 호출을 Yahoo Finance로 전환하여 분당 8건 제한 부담을 해소한다. TwelveDataClient는 예비 fallback으로 보존.
>
> **Project**: nowini
> **Version**: v0.1.2
> **Author**: wonseok-han
> **Date**: 2026-04-23
> **Status**: Draft

---

## Executive Summary

| Perspective | Content |
|-------------|---------|
| **Problem** | TwelveData 무료 플랜(8 req/min, 800 req/day)이 인트라데이 캔들과 시장 지수 조회의 병목. 동시 사용자 증가 시 rate limit 에러 빈발 우려. |
| **Solution** | TwelveData가 담당하는 2가지 역할(인트라데이 5분봉, 시장 지수/환율 fallback)의 주 소스를 Yahoo Finance로 전환. TwelveDataClient는 최후방 fallback으로 보존. |
| **Function/UX Effect** | 사용자 체감: D1 차트·시장 지수 로딩 실패율 감소, 응답 속도 개선. 운영: TwelveData rate limit 부담 대폭 감소. |
| **Core Value** | **API 부담 분산**. 일상 호출을 무료 무제한 Yahoo Finance로 이관하여 TwelveData 쿼터를 아끼고, 필요 시 활용할 수 있는 여유 확보. |

---

## 1. Overview

### 1.1 Purpose

TwelveData API의 일상 호출을 Yahoo Finance로 전환하여 rate limit 부담을 해소한다. TwelveDataClient 코드와 설정은 예비 fallback으로 보존한다.

### 1.2 Background

현재 TwelveData는 두 곳에서 사용 중:

1. **CandleService** — D1(인트라데이) 5분봉 조회 (`timeSeries()`)
2. **MarketOverviewService** — 시장 지수(SPX, IXIC, DJI, VIX) 및 USD/KRW 환율의 fallback (`quote()`)

Yahoo Finance는 이미 프로젝트에 통합되어 있으며(일봉 OHLCV, 실시간 시세), 동일한 데이터를 API 키 없이 제공할 수 있다.

### 1.3 Related Documents

- 기획: [`docs/planning/04-data-sources.md`](../../planning/04-data-sources.md) — 데이터 소스 전략
- 설계: [`docs/02-design/features/phase4.5-improvements.design.md`](../../02-design/features/phase4.5-improvements.design.md) — CandleService DB-first 설계

---

## 2. Scope

### 2.1 In Scope

| # | 항목 | 설명 |
|---|------|------|
| 1 | YahooFinanceClient 인트라데이 지원 | 5분봉 캔들 조회 메서드 추가 (`fetchIntradayCandles`) |
| 2 | CandleService 전환 | D1 인트라데이 소스를 TwelveData → Yahoo Finance로 변경 |
| 3 | MarketOverviewService 전환 | 지수/환율 fallback을 TwelveData → Yahoo Finance로 변경 |
| 4 | Yahoo 심볼 매핑 | 지수 심볼 변환 (SPX→`^GSPC`, IXIC→`^IXIC`, DJI→`^DJI`, VIX→`^VIX`, USD/KRW→`USDKRW=X`) |
| 5 | TwelveData fallback 강등 | CandleService·MarketOverviewService에서 TwelveData를 최후방 fallback으로 배치 (코드·설정 보존) |
| 6 | TimeFrame enum 리팩터링 | `twelveDataInterval()` 외에 Yahoo interval 매핑 추가 |

### 2.2 Out of Scope

- Yahoo Finance API 외의 새로운 데이터 소스 추가
- 프론트엔드 변경 (BE API 응답 스키마 변경 없음)
- 캔들 DB 스키마 변경

---

## 3. Requirements

### 3.1 Functional Requirements

| ID | 요구사항 | 우선순위 |
|----|----------|----------|
| FR-01 | Yahoo Finance로 5분봉 인트라데이 캔들을 조회할 수 있어야 한다 | P0 |
| FR-02 | Yahoo Finance로 시장 지수(S&P500, Nasdaq, Dow, VIX) 시세를 조회할 수 있어야 한다 | P0 |
| FR-03 | Yahoo Finance로 USD/KRW 환율을 조회할 수 있어야 한다 | P0 |
| FR-04 | TwelveDataClient·설정을 보존하되, 일상 호출에서 빠지고 최후방 fallback으로만 동작해야 한다 | P0 |
| FR-05 | 기존 API 응답 스키마(Quote, Candle, MarketOverviewResponse)가 변경되지 않아야 한다 | P0 |

### 3.2 Non-Functional Requirements

| ID | 요구사항 | 기준 |
|----|----------|------|
| NFR-01 | 인트라데이 캔들 응답 시간 | 3초 이내 (Redis 캐시 miss 시) |
| NFR-02 | 시장 지수 조회 응답 시간 | 5초 이내 (전체 overview) |
| NFR-03 | Yahoo Finance 장애 시 graceful degradation | null/빈 리스트 반환, 500 에러 없이 |

---

## 4. Technical Approach

### 4.1 YahooFinanceClient 확장

기존 `YahooFinanceClient`에 인트라데이 캔들 메서드 추가:

```
GET /v8/finance/chart/{symbol}?interval=5m&range=1d
```

- 응답에서 `timestamp[]` + `indicators.quote[0]` (open/high/low/close/volume) 파싱
- 기존 `parseChartResponse()` 로직 재활용 (CandleEntity 대신 Candle 도메인 반환)
- Yahoo는 UTC 기준 epoch 반환 → TwelveData와 동일한 Candle 도메인으로 매핑

### 4.2 CandleService 변경

```
Before: D1 → TwelveData
After:  D1 → Yahoo Finance → TwelveData (fallback)
```

- 주 소스: YahooFinanceClient.fetchIntradayCandles(ticker)
- Yahoo 실패 시: 기존 TwelveDataClient.timeSeries() fallback
- Redis 캐시 키·TTL(5분) 유지
- 에러 핸들링 동일 (둘 다 실패 → TICKER_NOT_FOUND)

### 4.3 MarketOverviewService 변경

fallback 체인을 3단계로 확장:

```
Before: Finnhub → TwelveData
After:  Finnhub → Yahoo Finance → TwelveData (최후방)
```

- Yahoo 심볼 매핑: `{Finnhub: "^GSPC", Yahoo: "^GSPC", display: "S&P 500"}`
- VIX: Yahoo에서 `^VIX`로 직접 조회 가능
- USD/KRW: Yahoo에서 `USDKRW=X`로 조회
- TwelveData는 Yahoo까지 실패했을 때의 최종 fallback으로 보존

### 4.4 TimeFrame enum 정리

```java
// Before
D1("5min", 78, 1, false)  // twelveDataInterval

// After  
D1("5m", 78, 1, false)    // yahooInterval (Yahoo Finance 형식)
```

- `twelveDataInterval()` → `interval()` 로 메서드명 변경
- Yahoo interval 형식: "5m", "1d" (TwelveData "5min", "1day"와 약간 다름)

### 4.5 TwelveData 보존 범위

| 파일 | 액션 |
|------|------|
| `TwelveDataClient.java` | 보존 (fallback으로 계속 사용) |
| `TwelveDataProperties.java` | 보존 |
| `application.yml` twelvedata 섹션 | 보존 |
| `.env.example` TWELVE_DATA_API_KEY | 보존 (optional로 표기) |

---

## 5. Risk & Mitigation

| 리스크 | 영향 | 완화 |
|--------|------|------|
| Yahoo Finance 비공식 API 불안정 | 인트라데이 캔들 조회 실패 | Redis 캐시(5분 TTL)로 호출 빈도 최소화. 장애 시 빈 차트 표시 (기존 graceful degradation 패턴) |
| Yahoo 인트라데이 데이터 지연 | TwelveData 대비 데이터 신선도 차이 | Yahoo도 ~1분 지연으로 TwelveData와 유사. 실사용 무차별 |
| Yahoo API 응답 형식 변경 | 파싱 실패 | 기존 `parseChartResponse()`와 동일한 defensive parsing. null-safe 처리 |

---

## 6. Implementation Steps

| Step | 작업 | 예상 변경 파일 |
|------|------|----------------|
| 1 | YahooFinanceClient에 `fetchIntradayCandles()` 메서드 추가 | `YahooFinanceClient.java` |
| 2 | TimeFrame enum에 Yahoo interval 매핑 추가 | `TimeFrame.java` |
| 3 | CandleService: 주 소스를 Yahoo로, TwelveData를 fallback으로 | `CandleService.java` |
| 4 | MarketOverviewService: Finnhub → Yahoo → TwelveData 3단 fallback | `MarketOverviewService.java` |
| 5 | 빌드 확인 및 수동 테스트 | — |

---

## 7. Success Criteria

- [ ] D1 인트라데이 차트가 Yahoo Finance 데이터로 정상 표시
- [ ] 시장 지수(S&P500, Nasdaq, Dow, VIX) 시세 정상 표시
- [ ] USD/KRW 환율 정상 표시
- [ ] TwelveData가 일상 호출에서 빠지고 최후방 fallback으로만 동작
- [ ] `./gradlew build` 성공
- [ ] 기존 FE가 변경 없이 정상 동작 (API 응답 스키마 호환)
