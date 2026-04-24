# Plan: api-cache-optimization

## Executive Summary

| 관점 | 요약 |
|------|------|
| **Problem** | 외부 API(FMP 250/일, Finnhub 60/분, TwelveData 800/일) 할당량이 1인 개발+테스트만으로도 한계에 도달. 장외시간(16시간+주말)에도 동일한 짧은 TTL로 불필요한 API 호출 발생 |
| **Solution** | MarketStatusResolver 기반 적응형 캐시 TTL — 장 상태(OPEN/CLOSED)에 따라 TTL을 동적 조절하고, FMP 원본 응답을 Redis에 통합 캐싱 |
| **Function UX Effect** | 사용자 체감 변화 없음 (동일한 데이터, 동일한 응답). 장외시간 API 에러/지연 감소 |
| **Core Value** | API 일일 호출량 60~80% 감소 → 무료 플랜 지속 가능성 확보 + 서비스 안정성 향상 |

---

## 1. Problem Statement

### 1.1 현재 상황

| API | 무료 한도 | 추정 일일 소모 | 여유도 |
|-----|:--------:|:------------:|:-----:|
| **FMP** | 250/일 | ~288 | **초과 위험** |
| **Finnhub** | 60/분 | ~384/일 | 보통 |
| **TwelveData** | 800/일 | 낮음 (fallback) | 여유 |
| **Yahoo Finance** | 무제한* | 중간 | TLS 429 리스크 |

> *Yahoo는 API key 불필요하나 TLS fingerprinting + rate limit으로 429 차단 빈번

### 1.2 핵심 문제

1. **장외시간 낭비**: 미국 정규장은 09:30-16:00 ET (6.5시간). 나머지 17.5시간+주말에도 동일한 짧은 TTL(30초~15분)로 API를 호출하지만 데이터는 변하지 않음
2. **FMP 한도 초과**: gainers+losers(2 call/15min) + sectors(1 call/15min) = 하루 ~288회, 무료 250회 초과
3. **개별 캐시의 비효율**: FMP market movers는 gainers+losers 2개 호출을 각각 하지만, 하나의 캐시 키(`market:movers`)에 합쳐 저장. 이미 원본 통합 캐싱의 필요성이 드러남

### 1.3 목표

- **정량**: 외부 API 일일 호출량 60% 이상 감소
- **정성**: 무료 플랜 내 안정적 운영, 장외시간 에러 최소화

---

## 2. Solution Overview

### 2.1 적응형 TTL 시스템

기존 `MarketStatusResolver.resolve()` → `OPEN`/`CLOSED` 판별 로직을 활용하여, 각 서비스의 캐시 TTL을 장 상태에 따라 동적 조절.

| 캐시 키 | 정규장 TTL | 장외 TTL | 변화율 | 근거 |
|---------|:----------:|:--------:|:-----:|------|
| `quote:{ticker}` | 30초 | **4시간** | -99% | 장외 시세 고정 |
| `market:overview` | 5분 | **1시간** | -92% | 지수/환율 장외 미변동 |
| `market:movers` | 15분 | **다음 개장까지** | -100% (장외 호출 0) | 모버스 장중에만 의미 |
| `market:sectors` | 15분 | **다음 개장까지** | -100% | 섹터 성과 장중에만 변동 |
| `candle:{ticker}:{tf}` | 5분 | **1시간** | -92% | 인트라데이 캔들 장외 불변 |
| `ind:{ticker}` | 5분 | **1시간** | -92% | 지표도 캔들 기반 |
| `market:news` | 15분 | **30분** | -50% | 뉴스는 장외에도 발생 |
| `ai:*` | 60분 | 60분 | 0% | AI 분석은 장 상태 무관 |
| `profile:*` | 24h | 24h | 0% | 이미 충분 |
| `overview:*` | 24h | 24h | 0% | 이미 충분 |
| `analyst:*` | 24h | 24h | 0% | 이미 충분 |
| `yahoo:summary:*` | 24h | 24h | 0% | 이미 충분 |

### 2.2 FMP 원본 캐싱

Yahoo `quoteSummary`와 동일한 패턴으로 FMP 응답도 원본 Redis 캐싱:

| FMP 엔드포인트 | Redis 키 | TTL (장중) | TTL (장외) |
|---------------|----------|:----------:|:----------:|
| `/biggest-gainers` + `/biggest-losers` | `fmp:movers` | 15분 | 다음 개장까지 |
| `/sector-performance` | `fmp:sectors` | 15분 | 다음 개장까지 |

### 2.3 "다음 개장까지" TTL 계산

`MarketStatusResolver`에 메서드 추가:

```
durationUntilNextOpen():
  현재 OPEN → Duration.ZERO (또는 매우 짧은 값)
  평일 장외 → 다음 날 09:30 ET까지
  금요일 장 마감 후 → 월요일 09:30 ET까지
  주말 → 월요일 09:30 ET까지
```

---

## 3. Scope

### 3.1 In Scope

- [ ] `MarketStatusResolver`에 `durationUntilNextOpen()` 메서드 추가
- [ ] `RedisCacheAdapter`에 적응형 TTL 헬퍼 추가 (또는 서비스별 TTL 분기)
- [ ] 단기 TTL 서비스 7개 수정 (quote, overview, movers, sectors, candle, indicator, news)
- [ ] FMP 원본 캐싱 (movers, sectors)
- [ ] 기존 `MarketStatusResolver` 단위테스트 보강

### 3.2 Out of Scope

- 공휴일 캘린더 (NYSE holiday schedule) — 향후 별도 feature
- FE 캐시 전략 변경 (React Query staleTime은 현행 유지)
- Yahoo v8/chart 엔드포인트 curl 전환 (별도 feature)
- 새로운 데이터 소스 추가

---

## 4. Acceptance Criteria

| # | 기준 | 검증 방법 |
|---|------|----------|
| AC-1 | 장외시간에 `market:movers`, `market:sectors` API 호출 0 | BE 로그 |
| AC-2 | 장외시간에 `quote:{ticker}` TTL이 4시간 | Redis TTL 확인 |
| AC-3 | 정규장 시간에는 기존 TTL과 동일하게 동작 | BE 로그 |
| AC-4 | FMP 일일 호출량 250회 이하 유지 | FMP 대시보드 |
| AC-5 | `make check` (FE + BE) 통과 | CI |
| AC-6 | 기존 기능 regression 없음 | 브라우저 검증 |

---

## 5. Risks & Mitigations

| 리스크 | 영향 | 대응 |
|--------|------|------|
| 장외시간 실제로 데이터 변하는 경우 (after-hours) | 낮음 — 현재 서비스에서 after-hours 데이터 미제공 | TTL 상한을 4시간으로 제한 (완전 캐시 잠금 아님) |
| MarketStatusResolver 공휴일 미대응 | 낮음 — 공휴일에 API 호출해도 빈 데이터 반환될 뿐 | v1에서는 주말만 처리, 공휴일은 향후 대응 |
| Redis 메모리 증가 | 매우 낮음 — 원본 JSON도 수 KB 수준 | Upstash 무료 256MB 한도 내 |

---

## 6. Implementation Approach

### 6.1 변경 영향도

| 변경 범위 | 파일 수 | 복잡도 |
|----------|:------:|:-----:|
| `MarketStatusResolver` 확장 | 1 | 낮음 |
| 서비스 TTL 분기 로직 | 7 | 중간 |
| FMP 원본 캐싱 | 2 | 낮음 |
| 테스트 | 1~2 | 낮음 |
| **합계** | **~12** | **중간** |

### 6.2 구현 순서 (제안)

1. `MarketStatusResolver.durationUntilNextOpen()` 구현 + 테스트
2. 서비스별 적응형 TTL 적용 (FMP 관련 우선)
3. FMP 원본 캐싱
4. 나머지 서비스 TTL 적용
5. 통합 검증

---

## 7. Dependencies

| 의존성 | 상태 | 비고 |
|--------|:----:|------|
| `MarketStatusResolver` | ✅ 존재 | `resolve()`, `OPEN`/`CLOSED` |
| `RedisCacheAdapter` | ✅ 존재 | `getOrLoad()` 패턴 |
| Upstash Redis | ✅ 운영 중 | 무료 256MB |

---

## 8. Success Metrics

| 지표 | 현재 | 목표 |
|------|:----:|:----:|
| FMP 일일 호출 | ~288 | **< 150** |
| Finnhub 일일 호출 | ~384 | **< 200** |
| 장외시간 API 에러율 | 산발적 429 | **0%** |
| 사용자 체감 변화 | — | **없음** (데이터 동일) |
