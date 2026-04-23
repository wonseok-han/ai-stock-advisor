# Plan: stock-detail-enrichment

## Executive Summary

| 관점 | 요약 |
|---|---|
| **Problem** | 종목 상세 페이지에 가격·차트·기술지표만 있고, 초보 투자자가 종목을 판단하는 데 필요한 펀더멘털 정보(P/E, EPS, 배당, 52주 고저 등)가 전혀 없음 |
| **Solution** | FMP Company Profile API + Yahoo Finance meta 필드를 활용하여 기업 개요·밸류에이션·52주 범위를 한눈에 보여주는 정보 카드 신설 |
| **Function UX Effect** | 종목 헤더 아래에 기업 개요 카드가 배치되어, 차트를 보기 전에 기업의 기본 체력(업종, 시총, P/E, 배당, 52주 위치)을 빠르게 파악 가능 |
| **Core Value** | "이 종목이 뭐하는 회사인지" 기본 정보 없이 AI 분석만 보던 UX의 정보 빈약함 해소 — 초보 투자자의 맥락 파악 능력 향상 |

---

## 1. Background & Motivation

### 현재 상태
- 종목 상세 페이지 구성: `StockHeader` → `TimeFrameTabs` → `ChartPanel` → `IndicatorsPanel` → `AiSignalPanel` → `NewsPanel`
- `StockProfile`에 이름, 거래소, 통화, 로고, 업종(industry), 시가총액(marketCap)만 존재
- `Quote`에 당일 OHLCV + 변동률만 존재
- **펀더멘털 데이터 전무**: P/E, EPS, 배당수익률, 52주 고저, 섹터, 베타 — 모두 없음

### 문제점
1. 초보 투자자가 종목의 기본 체력을 판단할 수 없음 (가격만으로는 고평가/저평가 판단 불가)
2. AI 참고 분석이 펀더멘털 컨텍스트 없이 제공되어 해석이 어려움
3. 경쟁 서비스(Yahoo Finance, Investing.com) 대비 정보 밀도가 현저히 낮음

### 해결 방향
- 기존 FMP 클라이언트를 확장하여 Company Profile 엔드포인트 추가
- Yahoo Finance quote meta에서 52주 고저 추출 (API 호출 추가 없음)
- 종목 헤더 아래 기업 개요 카드(CompanyOverviewPanel) 신설

---

## 2. Goals & Non-Goals

### Goals
- 종목 상세 페이지에서 기업의 핵심 펀더멘털 6가지를 한눈에 확인
- 52주 고저 대비 현재가 위치를 시각적으로 표현 (Progress Bar)
- FMP 무료 플랜(250 req/day) 내 운영 — 24시간 Redis 캐시
- 모바일 반응형: 2열 그리드 (데스크탑) → 1열 스택 (모바일)

### Non-Goals
- 재무제표 (Income Statement, Balance Sheet) 상세 — 향후 별도 feature
- 애널리스트 평점/목표가/실적 — 다음 PDCA 사이클 (`analyst-ratings`)
- 종목 비교 기능
- 과거 P/E 추세 차트

---

## 3. Data Sources & API

### 3.1 FMP Company Profile API (신규)

**엔드포인트**: `GET /profile/{symbol}?apikey={key}`

**반환 필드 (사용할 것들)**:
| FMP 필드 | 도메인 매핑 | 비고 |
|---|---|---|
| `sector` | sector | Finnhub은 industry만 제공, FMP는 sector 별도 |
| `industry` | industry | Finnhub 대비 더 세분화됨 |
| `mktCap` | marketCap | 실시간 시총 (Finnhub보다 정확) |
| `price` | — | 사용 안 함 (Quote에서 가져옴) |
| `beta` | beta | 변동성 지표 |
| `lastDiv` | dividendPerShare | 최근 배당금 |
| `companyName` | — | profile.name과 중복 |
| `description` | description | 기업 설명 (영문, 한국어 번역 미적용) |
| `ceo` | — | 사용 안 함 (정보 과잉) |
| `fullTimeEmployees` | employees | 임직원 수 |
| `website` | website | 공식 홈페이지 |
| `ipoDate` | ipoDate | 상장일 |

**Rate Limit**: 250 req/day (무료 플랜). 24시간 캐시로 약 250 종목/일 커버 가능. 현 베타 트래픽 기준 충분.

### 3.2 Yahoo Finance v8 meta (기존 확장)

`YahooFinanceClient.quote()` 응답의 `meta` 필드에서 추가 추출:
| Yahoo 필드 | 도메인 매핑 |
|---|---|
| `fiftyTwoWeekHigh` | week52High |
| `fiftyTwoWeekLow` | week52Low |
| `trailingPE` | peRatio |
| `epsTrailingTwelveMonths` | eps |

> Yahoo Finance meta에서 PE/EPS를 못 가져올 경우 FMP fallback 사용.

---

## 4. Functional Requirements

| ID | 요구사항 | 우선순위 |
|---|---|---|
| FR-01 | FmpClient에 `companyProfile(ticker)` 메서드 추가 | Must |
| FR-02 | CompanyOverview 도메인 레코드 생성 (sector, industry, marketCap, peRatio, eps, dividendPerShare, beta, week52High, week52Low, description, employees, website, ipoDate) | Must |
| FR-03 | YahooFinanceClient.quote()에서 fiftyTwoWeekHigh/Low 추출 → Quote 확장 | Must |
| FR-04 | CompanyOverviewService: FMP → Redis 캐시(24h TTL) | Must |
| FR-05 | StockController에 `GET /stocks/{ticker}/overview` 엔드포인트 추가 | Must |
| FR-06 | StockDetail 응답에 `overview` 필드 포함 (aggregate) | Must |
| FR-07 | FE CompanyOverviewPanel 컴포넌트: 52주 범위 프로그레스 바 + 핵심 지표 그리드 | Must |
| FR-08 | FE CompanyOverviewPanel: 반응형 (데스크탑 2열, 모바일 1열) | Must |
| FR-09 | FE StockDetail 타입에 CompanyOverview 인터페이스 추가 | Must |
| FR-10 | Quote에 week52High/week52Low 필드 추가 (BE + FE 타입 동기화) | Must |
| FR-11 | P/E, 시총, 배당 등 숫자 포맷 유틸 (formatMarketCap: $1.2T, formatPE: 28.5x 등) | Must |
| FR-12 | 기업 설명 접이식 (3줄 초과 시 "더 보기" 토글) | Should |
| FR-13 | 52주 고저 범위 바에 현재가 위치 도트 표시 | Must |
| FR-14 | FMP API 실패 시 graceful degradation — 패널 자체를 숨기되 나머지 정상 표시 | Must |

---

## 5. Non-Functional Requirements

| ID | 요구사항 |
|---|---|
| NFR-01 | FMP Company Profile: Redis 캐시 24시간 TTL |
| NFR-02 | Quote 52주 고저: 기존 Quote 캐시(30s)에 포함 — 추가 호출 없음 |
| NFR-03 | CompanyOverview API 응답 < 200ms (캐시 히트 시) |
| NFR-04 | FMP 무료 250 req/day 초과 방지: 캐시 미스 시만 호출 |
| NFR-05 | 기존 StockDetail aggregate 응답에 overview 추가 시 하위 호환 유지 (nullable) |

---

## 6. Implementation Scope

### BE (Spring Boot)

| Step | 파일 | 변경 |
|---|---|---|
| 1 | `stock/domain/CompanyOverview.java` | 신규 record (13 fields) |
| 2 | `stock/domain/Quote.java` | `week52High`, `week52Low` 필드 추가 |
| 3 | `market/infra/FmpClient.java` | `companyProfile(ticker)` 메서드 + `FmpProfile` inner record 추가 |
| 4 | `stock/infra/client/YahooFinanceClient.java` | `quote()` meta에서 fiftyTwoWeekHigh/Low + trailingPE + eps 추출 |
| 5 | `stock/service/CompanyOverviewService.java` | 신규 서비스: FMP → Redis 24h 캐시 |
| 6 | `stock/web/StockController.java` | `GET /stocks/{ticker}/overview` 엔드포인트 |
| 7 | `stock/web/StockDetailResponse.java` | `overview` 필드 추가 |
| 8 | `stock/service/StockDetailService.java` | aggregate에 overview 병합 |

### FE (Next.js)

| Step | 파일 | 변경 |
|---|---|---|
| 9 | `types/stock.ts` | `CompanyOverview` 인터페이스 + Quote에 week52High/Low 추가 + StockDetail에 overview 필드 |
| 10 | `lib/format/number.ts` | `formatMarketCap`, `formatPE`, `formatEps`, `formatDividendYield` 유틸 |
| 11 | `lib/api/stocks.ts` | `fetchCompanyOverview(ticker)` 함수 추가 |
| 12 | `features/stock-detail/hooks/use-company-overview.ts` | React Query 훅 (staleTime 24h) |
| 13 | `features/stock-detail/components/company-overview-panel.tsx` | 기업 개요 카드 UI 컴포넌트 |
| 14 | `features/stock-detail/stock-detail-view.tsx` | CompanyOverviewPanel 배치 (헤더 ↔ 타임프레임탭 사이) |

### 변경 없는 파일
- `FinnhubClient` — profile 역할을 FMP에 위임하지 않음 (기존 profile 그대로 유지)
- `TwelveDataClient` — 펀더멘털 데이터 없음
- `CandleService`, `AiSignalService`, `NewsService` — 영향 없음

---

## 7. UI Layout

### CompanyOverviewPanel (데스크탑 — 2열 그리드)

```
┌──────────────────────────────────────────────────────┐
│  기업 개요                                            │
├───────────────────────────┬──────────────────────────┤
│  섹터       Technology    │  시가총액    $3.4T        │
│  업종       Consumer      │  P/E        28.5x        │
│             Electronics   │  EPS        $6.73        │
│  베타       1.24          │  배당수익률  0.44%        │
├───────────────────────────┴──────────────────────────┤
│  52주 범위                                            │
│  $164.08 ├══════════════════════●══════┤ $260.10     │
│                              $227.48                  │
├──────────────────────────────────────────────────────┤
│  Apple Inc. designs, manufactures, and markets       │
│  smartphones, personal computers, tablets...         │
│  [더 보기]                                           │
└──────────────────────────────────────────────────────┘
```

### 모바일 (1열 스택)
- 지표 그리드: 2x3 → 1x6 세로 스택
- 52주 범위 바: 풀 너비
- 기업 설명: 기본 접힘 (2줄)

---

## 8. Risks & Mitigations

| 리스크 | 영향 | 완화 |
|---|---|---|
| FMP 무료 플랜 250 req/day 소진 | 신규 종목 overview 미표시 | 24h 캐시 + graceful degradation (패널 숨김) |
| Yahoo Finance meta에 PE/EPS 없는 종목 | 일부 지표 null | FMP에서 보완, null이면 "—" 표시 |
| FMP 응답 지연/실패 | overview 패널 로딩 실패 | CompletableFuture로 타임아웃 분리, 나머지 정상 반환 |
| ETF/인덱스 종목에 PE/EPS 없음 | 지표 대부분 null | ETF 감지 시 패널 내용 조정 (시총·설명만 표시) |

---

## 9. Acceptance Criteria

| # | 기준 |
|---|---|
| AC-01 | AAPL 종목 상세에서 섹터(Technology), 시총($3.4T), P/E, EPS, 배당, 52주 범위가 모두 표시됨 |
| AC-02 | 52주 범위 바에 현재가 위치가 정확히 표시됨 (비율 계산 검증) |
| AC-03 | FMP API 실패 시 overview 패널만 사라지고, 나머지(차트·지표·AI·뉴스) 정상 작동 |
| AC-04 | 동일 종목 재방문 시 FMP 호출 없이 Redis 캐시에서 즉시 반환 (24h 이내) |
| AC-05 | 모바일(375px)에서 overview 카드 레이아웃 정상 표시 |
| AC-06 | `make web-check` + `make api-check` 통과 |
| AC-07 | Quote에 week52High/Low가 포함되어 StockHeader에서도 접근 가능 |

---

## 10. Out of Scope (다음 PDCA)

- 애널리스트 평점/목표가 (`analyst-ratings`)
- 분기별 실적 (Earnings) 표
- 재무제표 상세 (Income/Balance/Cash Flow)
- 기업 설명 한국어 번역
- 동종업계 비교 차트

---

## 11. Dependencies

| 의존성 | 상태 | 비고 |
|---|---|---|
| FMP API Key (`FMP_API_KEY`) | ✅ 설정 완료 | application.yml에 이미 존재 |
| FmpClient | ✅ 존재 | `market/infra/FmpClient.java` — gainers/losers만, 확장 필요 |
| FmpProperties | ✅ 존재 | baseUrl + apiKey 바인딩 완료 |
| Redis 캐시 | ✅ 운영 중 | `RedisCacheAdapter` 재사용 |
| Yahoo Finance | ✅ 운영 중 | quote() meta 확장만 필요 |
