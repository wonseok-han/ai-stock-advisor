# api-cache-optimization Gap Analysis Report

> **Feature**: api-cache-optimization
> **Analysis Date**: 2026-04-24
> **Design Document**: `docs/02-design/features/api-cache-optimization.design.md`
> **Files Analyzed**: 14개 (13 source + 1 test)

---

## Overall Score

| Category | Score | Status |
|----------|:-----:|:------:|
| Design Match | 97% | PASS |
| Architecture Compliance | 100% | PASS |
| Convention Compliance | 100% | PASS |
| Test Coverage | 100% | PASS |
| **Overall** | **98%** | **PASS** |

---

## 1. MarketStatusResolver.java

| Item | Design | Implementation | Match |
|------|--------|----------------|:-----:|
| `durationUntilNextOpen()` public static | Section 3.1 | Line 46-48 | PASS |
| `durationUntilNextOpen(ZonedDateTime)` package-private | Section 8.2 | Line 50-55 | PASS |
| `resolve(ZonedDateTime)` package-private | Section 8.2 | Line 25-33 | PASS |
| `nextOpenTime(ZonedDateTime)` private | Section 3.1 | Line 57-69 | PASS |
| `isWeekday(DayOfWeek)` private | Section 3.1 | Line 71-73 | PASS |
| OPEN → Duration.ZERO | Section 3.1 | Line 51-52 | PASS |
| Weekend skip logic | Section 3.1 | Line 64-67 | PASS |

---

## 2. Service Layer — Adaptive TTL

| Service | Cache Key | 장중 TTL | 장외 TTL | Match |
|---------|-----------|:--------:|:--------:|:-----:|
| QuoteService | `quote:{ticker}` | 30s | `durationUntilNextOpen()` | PASS |
| MarketOverviewService | `market:overview` | 5min | `durationUntilNextOpen()` | PASS |
| MarketMoversService | `market:movers` | 15min | `durationUntilNextOpen()` | PASS |
| SectorPerformanceService | `market:sectors` | 15min | `durationUntilNextOpen()` | PASS |
| CandleService | `candle:{ticker}:{tf}` | 5min | `durationUntilNextOpen()` | PASS |
| IndicatorService | `ind:{ticker}` | 5min | `durationUntilNextOpen()` | PASS |
| MarketNewsService | `market:news` | 15min | **30min (고정)** | PASS |

---

## 3. Client Layer — Raw API Response Caching

### FmpClient (8개)

| Cache Key | 장중 TTL | 장외 TTL | Match |
|-----------|:--------:|:--------:|:-----:|
| `fmp:gainers` | 15min | `durationUntilNextOpen()` | PASS |
| `fmp:losers` | 15min | `durationUntilNextOpen()` | PASS |
| `fmp:sectors` | 15min | `durationUntilNextOpen()` | PASS |
| `fmp:profile:{ticker}` | 24h | 24h | PASS |
| `fmp:ratios:{ticker}` | 24h | 24h | PASS |
| `fmp:grades:{ticker}` | 24h | 24h | PASS |
| `fmp:price-target:{ticker}` | 24h | 24h | PASS |
| `fmp:estimates:{ticker}` | 24h | 24h | PASS |

### FinnhubClient (2개)

| Cache Key | 장중 TTL | 장외 TTL | Match |
|-----------|:--------:|:--------:|:-----:|
| `finnhub:quote:{ticker}` | 30s | `durationUntilNextOpen()` | PASS |
| `finnhub:profile:{ticker}` | 24h | 24h | PASS |

### FinnhubMarketNewsClient (1개)

| Cache Key | 장중 TTL | 장외 TTL | Match |
|-----------|:--------:|:--------:|:-----:|
| `finnhub:news:general` | 15min | **30min (고정)** | PASS |

### TwelveDataClient (2개)

| Cache Key | 장중 TTL | 장외 TTL | Match |
|-----------|:--------:|:--------:|:-----:|
| `twelve:quote:{symbol}` | 30s | `durationUntilNextOpen()` | PASS |
| `twelve:series:{symbol}:{interval}` | 5min | `durationUntilNextOpen()` | PASS |

### YahooFinanceClient (3개)

| Cache Key | 장중 TTL | 장외 TTL | Match |
|-----------|:--------:|:--------:|:-----:|
| `yahoo:chart:{ticker}` | 30s | `durationUntilNextOpen()` | PASS |
| `yahoo:intraday:{ticker}` | 5min | `durationUntilNextOpen()` | PASS |
| `yahoo:summary:{ticker}` | 24h | 24h | PASS |

---

## 4. Test Coverage (M8-M13)

| ID | Scenario | Design | Implementation | Match |
|----|----------|--------|----------------|:-----:|
| M8 | 정규장 중 (수 14:00) | Duration.ZERO | `isEqualTo(Duration.ZERO)` | PASS |
| M9 | 평일 장 마감 후 (수 18:00) | >0, <16h | `isEqualTo(15h30m)` | PASS |
| M10 | 평일 장 개시 전 (목 07:00) | >0, ≤2.5h | `isEqualTo(2h30m)` | PASS |
| M11 | 금요일 장 마감 후 (금 18:00) | >60h | `isEqualTo(63h30m)` | PASS |
| M12 | 토요일 (토 12:00) | >0, <50h | `isEqualTo(45h30m)` | PASS |
| M13 | 일요일 (일 12:00) | >0, <22h | `isEqualTo(21h30m)` | PASS |

---

## 5. Minor Gaps (문서 내부 불일치)

| # | Location | Issue | Severity |
|---|----------|-------|:--------:|
| 1 | Design §5.1 QuoteService 설명 | "30s→4h"로 기재, 실제는 `durationUntilNextOpen()` | Minor (doc) |
| 2 | Design §5.1 MarketOverviewService 설명 | "5m→1h"로 기재, 실제는 `durationUntilNextOpen()` | Minor (doc) |
| 3 | Design §5.1 CandleService 설명 | "5m→1h"로 기재, 실제는 `durationUntilNextOpen()` | Minor (doc) |
| 4 | Design §5.1 IndicatorService 설명 | "5m→1h"로 기재, 실제는 `durationUntilNextOpen()` | Minor (doc) |

모두 설계 문서 §5.1 설명란의 구 버전 잔존. §3.2 정규 TTL 표는 정확함. 구현 코드에 Gap 없음.

---

## 6. Conclusion

**Match Rate: 98%** — PASS (≥90% threshold)

- Critical Gap: 0건
- Major Gap: 0건
- Minor Gap: 4건 (설계 문서 내부 표기, 구현 무관)
- 구현 코드는 설계 의도를 100% 충족
- 뉴스 TTL 예외 처리(고정 30분) 정확히 반영
- 2중 캐시 구조(Client + Service) 완전 구현
- 테스트 6개 케이스 모두 설계 초과 충족(범위 검증 → 정확 값 검증)

**Next Step**: `/pdca report api-cache-optimization`
