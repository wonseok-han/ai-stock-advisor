# Plan: analyst-estimates

## Executive Summary

| 관점 | 요약 |
|---|---|
| **Problem** | 종목 상세에 펀더멘털(P/E, 52주 고저)은 추가됐지만 "월가가 이 종목을 어떻게 보는지" — 애널리스트 컨센서스·목표가·분기 실적 정보가 없어, 초보 투자자가 시장의 기대치를 파악할 수 없음 |
| **Solution** | Yahoo Finance quoteSummary 모듈 확장(financialData + recommendationTrend + earningsHistory)으로 애널리스트 평점/목표가/실적 Beat 데이터를 수집하고, FMP를 fallback으로 활용 |
| **Function UX Effect** | 종목 상세에 "애널리스트 컨센서스" 패널 추가 — 평점 게이지 + 목표가 바 차트 + 분기 실적 Beat/Miss 히스토리를 한눈에 확인 |
| **Core Value** | "전문가들은 이 종목을 어떻게 보나?" 질문에 즉답 — 초보 투자자의 종목 판단 맥락을 펀더멘털 → 시장 기대치로 확장 |

---

## 1. Background & Motivation

### 현재 상태
- 종목 상세 페이지: StockHeader → CompanyOverviewPanel → TimeFrameTabs → ChartPanel → IndicatorsPanel → AiSignalPanel → NewsPanel
- CompanyOverviewPanel에 섹터, 시총, P/E, EPS, 배당, 베타, 52주 범위 제공 (stock-detail-enrichment)
- **애널리스트 관련 데이터 전무**: 컨센서스 평점, 목표가, 분기 실적 — 모두 없음

### 문제점
1. 초보 투자자가 현재 주가가 시장 기대 대비 어디에 있는지 판단 불가 (목표가 대비 upside/downside)
2. 분기 실적이 기대치를 넘었는지(Beat) 못 미쳤는지(Miss) 알 수 없어 주가 변동 원인 이해 어려움
3. 경쟁 서비스(Yahoo Finance, Seeking Alpha, Investing.com) 모두 애널리스트 컨센서스를 기본 제공

### 해결 방향
- Yahoo Finance quoteSummary에 `financialData` + `recommendationTrend` + `earningsHistory` 모듈 추가 (기존 crumb 인증 재활용)
- FMP `analyst-stock-recommendations` + `price-target-consensus` + `earnings-surprises` API를 fallback으로 활용
- 종목 상세에 "애널리스트 컨센서스" 패널 신설

---

## 2. Goals & Non-Goals

### Goals
- 애널리스트 컨센서스 평점 표시 (Strong Buy ~ Strong Sell 분포 + 평균 점수)
- 목표가 표시 (현재가 대비 High/Low/Mean + Upside/Downside %)
- 분기 실적 표시 (최근 4분기 EPS actual vs estimate + Beat/Miss/Meet)
- Yahoo Finance primary + FMP fallback 이중 소스
- Redis 24시간 캐시 (애널리스트 데이터는 일간 변동이 적음)
- 모바일 반응형 UI

### Non-Goals
- 개별 애널리스트별 평점/목표가 리스트 (데이터량 과다, UX 복잡)
- 연간/분기 재무제표 상세 (Income Statement, Balance Sheet) — 별도 feature
- 실적 발표 캘린더/알림 — 별도 feature
- 매출(Revenue) 실적 비교 — EPS에 집중 (초보자 핵심 지표)

---

## 3. Data Sources & API

### 3.1 Yahoo Finance quoteSummary (Primary)

기존 `quoteSummary` 호출에 모듈 3개 추가:

| 모듈 | 제공 데이터 | 비용 |
|------|------------|------|
| `financialData` | targetHighPrice, targetLowPrice, targetMeanPrice, targetMedianPrice, recommendationMean, recommendationKey, numberOfAnalystOpinions, currentPrice | 무료 (기존 crumb 인증) |
| `recommendationTrend` | strongBuy, buy, hold, sell, strongSell 카운트 (월별 4개 기간) | 무료 |
| `earningsHistory` | epsActual, epsEstimate, epsDifference, surprisePercent (최근 4분기) | 무료 |

**장점**: API 키 불필요, 기존 crumb/cookie 재활용, 추가 비용 0
**리스크**: Yahoo rate limit (분당 ~100), 비공식 API

### 3.2 FMP (Fallback)

| 엔드포인트 | 제공 데이터 | 한도 |
|-----------|------------|------|
| `/analyst-stock-recommendations/{ticker}` | 개별 애널리스트 평점 리스트 | 250 req/day |
| `/price-target-consensus/{ticker}` | targetHigh, targetLow, targetMean, targetMedian | 250 req/day |
| `/earnings-surprises/{ticker}` | actualEarningResult, estimatedEarning, date | 250 req/day |

**장점**: 안정적 API, JSON 스키마 명확
**단점**: 무료 250 req/day 공유 (CompanyProfile + SectorPerformance와 경쟁)

### 3.3 Fallback 전략

```
Yahoo quoteSummary (financialData + recommendationTrend + earningsHistory)
  ↓ 실패 시
FMP (analyst-recommendations + price-target-consensus + earnings-surprises)
  ↓ 모두 실패 시
null → 패널 숨김 (graceful degradation)
```

### 3.4 캐시 전략

| 키 | TTL | 근거 |
|----|-----|------|
| `analyst:{ticker}` | 24시간 | 애널리스트 데이터는 일 단위로 갱신 |

---

## 4. Feature Requirements

### FR-01: 애널리스트 평점 게이지
- 1.0(Strong Buy) ~ 5.0(Strong Sell) 숫자 점수 + 한국어 라벨
- Strong Buy / Buy / Hold / Sell / Strong Sell 분포 막대 차트
- 총 애널리스트 수 표시

### FR-02: 목표가 레인지
- 현재가 vs 목표가(Mean) → Upside/Downside % 표시
- High / Mean / Low 레인지 바 (현재가 위치 마커)
- 목표가 대비 현재가 위치를 시각적으로 직관 표현

### FR-03: 분기 실적 히스토리
- 최근 4분기 EPS: Actual vs Estimate
- Beat(초과) / Meet(부합) / Miss(미달) 라벨 + 색상
- Surprise % 표시

### FR-04: InfoTooltip
- 각 섹션에 초보자용 한국어 설명 툴팁

### FR-05: Graceful Degradation
- 애널리스트 데이터 없는 종목(소형주 등) → 패널 자체 숨김
- 부분 데이터 → 가용한 섹션만 표시

---

## 5. Acceptance Criteria

| AC | 설명 | 검증 방법 |
|----|------|----------|
| AC-1 | AAPL 검색 시 애널리스트 패널에 평점, 목표가, 실적 3개 섹션 표시 | 브라우저 확인 |
| AC-2 | 평점 게이지에 1.0~5.0 점수 + 한국어 라벨 + 분포 차트 표시 | 브라우저 확인 |
| AC-3 | 목표가 레인지 바에 현재가 마커 + upside/downside % 표시 | 브라우저 확인 |
| AC-4 | 최근 4분기 EPS actual vs estimate + Beat/Miss 라벨 표시 | 브라우저 확인 |
| AC-5 | Yahoo 실패 시 FMP fallback으로 데이터 표시 | 로그 확인 |
| AC-6 | 데이터 없는 종목(소형주) → 패널 숨김 | 브라우저 확인 |
| AC-7 | Redis 24시간 캐시 적용 | 캐시 히트 로그 |
| AC-8 | `tsc --noEmit` + `gradlew check` 통과 | CLI 확인 |
| AC-9 | 모바일 반응형 (768px 이하 1열) | 브라우저 확인 |

---

## 6. Implementation Scope

### BE (신규/수정)
| 파일 | 변경 |
|------|------|
| `AnalystEstimates.java` | 신규 도메인 record (rating, priceTarget, earnings) |
| `AnalystEstimatesService.java` | 신규 서비스 (Yahoo primary + FMP fallback + 24h 캐시) |
| `YahooFinanceClient.java` | quoteSummary 모듈 확장 + 파싱 메서드 추가 |
| `FmpClient.java` | analyst-recommendations, price-target-consensus, earnings-surprises 3개 엔드포인트 추가 |
| `StockController.java` | `GET /stocks/{ticker}/analyst` 엔드포인트 추가 |

### FE (신규/수정)
| 파일 | 변경 |
|------|------|
| `types/stock.ts` | AnalystEstimates 타입 추가 |
| `lib/api/stock.ts` | getAnalystEstimates() 함수 추가 |
| `features/stock-detail/analyst/use-analyst-estimates.ts` | React Query 훅 |
| `features/stock-detail/analyst/analyst-panel.tsx` | 메인 패널 컴포넌트 |
| `features/stock-detail/analyst/components/rating-gauge.tsx` | 평점 게이지 |
| `features/stock-detail/analyst/components/price-target-bar.tsx` | 목표가 레인지 바 |
| `features/stock-detail/analyst/components/earnings-history.tsx` | 분기 실적 |
| `app/stock/[ticker]/page.tsx` | 패널 배치 (CompanyOverview 아래) |

### 예상 규모
- BE: ~5 파일, ~400줄
- FE: ~8 파일, ~600줄
- 총: ~13 파일, ~1,000줄

---

## 7. Risks & Mitigation

| 리스크 | 영향 | 대응 |
|--------|------|------|
| Yahoo quoteSummary rate limit | 데이터 못 가져옴 | FMP fallback + 24h 캐시로 호출 최소화 |
| FMP 250 req/day 소진 | fallback 불가 | 24h 캐시, CompanyProfile과 호출 예산 분배 |
| 소형주/ETF 애널리스트 데이터 부재 | 빈 패널 | graceful degradation (패널 숨김) |
| Yahoo 비공식 API 스키마 변경 | 파싱 실패 | @JsonIgnoreProperties + nullable 필드 + FMP fallback |

---

## 8. Dependencies

- Yahoo Finance quoteSummary crumb/cookie 인증 (기존 구현 재활용)
- FMP API 키 (기존 환경 변수)
- Redis 캐시 어댑터 (기존 RedisCacheAdapter)
- InfoTooltip 컴포넌트 (기존 @floating-ui/react)

---

## 9. Timeline

단일 PDCA 사이클, 예상 1일.
