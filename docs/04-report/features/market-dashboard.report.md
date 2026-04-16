# market-dashboard Completion Report

> **Feature**: Phase 3 — Market Dashboard
> **Project**: AI Stock Advisor
> **Date**: 2026-04-16
> **Branch**: `feat/market-dashboard`
> **Status**: Completed

---

## Executive Summary

### 1.1 Overview

| 항목 | 값 |
|---|---|
| Feature | Phase 3: Market Dashboard |
| PDCA 시작 | 2026-04-16 |
| PDCA 완료 | 2026-04-16 |
| 소요 시간 | ~3시간 (단일 세션) |
| Commits | 2 (docs + feat) |
| Match Rate | **95%** |
| Iteration | 0 (Act 불필요) |

### 1.2 Results

| 메트릭 | 값 |
|---|---|
| 신규 파일 | 25개 |
| 추가 Lines | 2,213 |
| BE 파일 | 12개 (도메인 5 + 서비스 3 + 컨트롤러 1 + 인프라 2 + 기존 수정 1) |
| FE 파일 | 11개 (컴포넌트 4 + hooks 3 + 타입 1 + API 1 + 기존 수정 1) |
| PDCA 문서 | 2개 (Plan + Design) |
| BE 빌드 | compileJava 성공 |
| FE 빌드 | next build 성공 |

### 1.3 Value Delivered

| 관점 | 계획 | 실제 결과 |
|---|---|---|
| **Problem** | 메인 페이지에 검색 박스만 존재, "오늘 시장이 어때?" 답변 불가 | 3개 BE 엔드포인트 + 대시보드 UI 완성으로 해결 |
| **Solution** | 주요 지수·VIX·환율·뉴스·급등락 종목을 한 화면에 제공 | MarketOverview(지수 4개+환율) + MarketNews(10건 번역) + MarketMovers(상위/하�� 5개) 구현 |
| **Function UX Effect** | 앱 진입 즉시 시장 분위기 파악 → 종목 상세 연결 | 지수 카드 클릭 UX, 종목 행 클릭→`/stock/{ticker}` 라우팅, VIX 색상 코딩 구현 |
| **Core Value** | "첫 화면이 오늘 시장 분위기에 대한 답" | 대시보드 통합 완료, 검색+시장정보 동시 제공, 면책 고지 유지 |

---

## 2. Implementation Summary

### 2.1 BE (Spring Boot)

#### 신규 패키지: `com.aistockadvisor.market`

| 파일 | 역할 |
|---|---|
| `MarketController` | 3개 REST 엔드포인트 (`/market/overview`, `/market/news`, `/market/movers`) |
| `MarketOverviewService` | 지수(S&P500, Nasdaq, Dow, VIX) + USD/KRW 조합, Finnhub→TwelveData fallback |
| `MarketNewsService` | Finnhub general news + NewsTranslator 한국어 번역, Redis 15분 캐시 |
| `MarketMoversService` | 인기 종목 30개 풀 → parallelStream quote → 변동률 정렬 (gainers/losers 각 5) |
| `MarketIndex` | 지수 DTO record |
| `MarketMover` | 급등/급락 종목 DTO record |
| `MarketMoversResponse` | movers 응답 DTO |
| `MarketOverviewResponse` | overview 응답 DTO |
| `MarketNewsItem` | 시장 뉴스 DTO |
| `FinnhubMarketNewsClient` | Finnhub `/news?category=general` 어댑터 |
| `PopularTickerPool` | 인기 종목 30개 상수 관리 |

#### 기존 코드 수정

| 파일 | 변경 내용 |
|---|---|
| `Disclaimers.java` | `MARKET`, `MARKET_NEWS`, `MARKET_MOVERS` 상수 3개 추가 |
| `TwelveDataClient.java` | `quote()` 메서드 + `TwelveQuoteResponse` DTO 추가 (지수 fallback) |

### 2.2 FE (Next.js)

#### 신규: `features/market-dashboard/`

| 파일 | 역할 |
|---|---|
| `market-dashboard.tsx` | 3개 위젯 조합 래퍼 (독립 섹션 로딩) |
| `market-overview.tsx` | 지수 카드 4개 그리드 + VIX 색상 코딩 + 환율 바 + 스켈레톤 |
| `market-movers.tsx` | 급등/급락 2-column + 종목 클릭 라우�� + 스켈레톤 |
| `market-news.tsx` | 뉴스 리스트 + 한국어/영문 fallback + 상대 시간 표시 |
| `hooks/use-market-overview.ts` | React Query hook (staleTime 5분) |
| `hooks/use-market-news.ts` | React Query hook (staleTime 15분) |
| `hooks/use-market-movers.ts` | React Query hook (staleTime 15분) |
| `lib/api/market.ts` | apiFetch 래퍼 함수 3개 |
| `types/market.ts` | TypeScript 인터페이스 5개 |

#### 기존 코드 수정

| 파일 | 변경 내용 |
|---|---|
| `app/page.tsx` | SearchBox + MarketDashboard 통합, max-width 확대 (3xl→5xl) |

### 2.3 캐시 전략

| Redis Key | TTL | 데이터 |
|---|---|---|
| `market:overview` | 5분 | 지수 + VIX + 환율 |
| `market:news` | 15분 | 시장 뉴스 10건 (번역 포함) |
| `market:movers` | 15분 | 급등/급락 각 5개 |

### 2.4 기존 코드 재사용

| 컴포넌트 | 재사용 방식 |
|---|---|
| `FinnhubClient.quote()` | 지수 quote + movers 종목 quote |
| `TwelveDataClient.quote()` | 신규 추가, 지수 fallback |
| `NewsTranslator.translate()` | 시장 뉴스 한국어 번역 |
| `RedisCacheAdapter.getOrLoad()` | 3개 서비스 모두 사용 |
| `StockProfileService.getProfile()` | movers 종목명 조회 |
| `apiFetch` (FE) | market API 함수 래핑 |

---

## 3. Gap Analysis Summary

### Match Rate: **95%**

| 카테고리 | 점수 |
|---|---|
| Design Match | 93% |
| Architecture Compliance | 100% |
| Convention Compliance | 100% |

### 발견된 Gap

| # | 유형 | 내용 | 영향 | 조치 |
|---|---|---|---|---|
| 1 | 누락 | `MarketMover.volume` 필드 (BE+FE) | Low | Phase 3.1 에서 추가 가능 |
| 2 | 미구현 | `usdKrwChange` 환율 전일 변동 계산 | Low | Phase 3.1 에서 개선 |

### 긍정적 차이

- `MarketDashboard` 래퍼 컴포넌트 추가 — 설계에 없었지만 조합 책임 분리로 구조 개선
- FE `usdKrw`/`usdKrwChange` nullable 처리 — BE 실제 동작에 더 정확히 대응

---

## 4. Architecture Decisions

| 결정 | 이유 | 대안 |
|---|---|---|
| Finnhub→TwelveData fallback | Finnhub 무료 지수 quote 지원 불확실 | 단일 소스 의존 시 전체 실패 |
| 인기 종목 30개 풀 상수 | Finnhub movers API 무료 미지원 | 크롤링(비추), 유료 API |
| Redis only 캐시 (DB 미사용) | 시장 뉴스는 빠르게 교체, DB 저장 불필요 | DB 24h 캐시 (기존 종목뉴스 패턴) |
| parallelStream (movers) | 30개 quote 순차 호출 시 90초+, 병렬로 3~5초 | ExecutorService (불필요한 복잡도) |
| 독립 React Query hooks | 섹션 간 에러 격리, 부분 실패 허용 | 단일 hook (한 섹션 실패=전체 실패) |

---

## 5. Known Limitations & Future Improvements

| 항목 | 현재 상태 | 개선 방안 (Phase 3.1+) |
|---|---|---|
| 지수 심볼 | Finnhub 미지원 시 TwelveData fallback | 배포 후 실제 소스 확정 필요 |
| VIX 데이터 | 조건부 렌더링 (미지원 시 숨김) | 대안 소스 탐색 |
| 환�� 전일 변동 | null 반환 | 전일 캐시 기반 delta 계산 |
| movers volume | 미포함 | MarketMover record 에 필드 추가 |
| 인기 종목 풀 | 30개 하드코딩 | 50개 확장 또는 DB 관리 |
| Render cold start | 대시보드 첫 로딩 1~2분 | Fly.io/유료 전환 시 해소 |

---

## 6. Commits

| Hash | Message |
|---|---|
| `b32b70f` | docs: add Phase 3 market-dashboard plan and design documents |
| `6d63769` | feat: implement Phase 3 market dashboard (BE + FE) |

---

## 7. PDCA Cycle Summary

```
[Plan] ✅ → [Design] ✅ → [Do] ✅ → [Check] ✅ (95%) → [Report] ✅
```

Phase 3 Market Dashboard PDCA 사이클이 완료되었습니다. 다음 단계로 `feat/market-dashboard` 브랜치를 `develop`에 PR 머지하면 개발 완료, 이후 `develop→main` 머지로 배포할 수 있습니다.
