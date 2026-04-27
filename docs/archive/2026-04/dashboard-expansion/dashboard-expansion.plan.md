# Plan: dashboard-expansion

## Executive Summary

| 관점 | 요약 |
|---|---|
| **Problem** | 메인 대시보드가 4개 지수 + 환율을 같은 그리드에 섞어 표시하고 있어, 지수와 환율/금리의 성격 차이가 드러나지 않으며, 섹터 퍼포먼스·국채·원자재 등 시장 전체를 파악하는 데 필요한 정보가 부재 |
| **Solution** | MarketOverview를 "지수" / "환율·금리" 두 섹션으로 분리하고, 섹터 퍼포먼스 위젯과 주요 원자재(금, 유가) 카드를 추가하여 시장 전체 온도를 한눈에 파악 가능하게 확장 |
| **Function UX Effect** | 대시보드 접속 시 지수→섹터→환율/금리/원자재 순서로 정보가 계층적으로 배치되어, "오늘 시장이 어떤가?"에 대한 답을 5초 안에 얻을 수 있음 |
| **Core Value** | 초보 투자자의 시장 맥락 파악 능력을 강화하여 "종목을 보기 전에 시장을 먼저 읽는" 습관 형성 — 서비스의 정보 밀도를 경쟁 수준으로 끌어올림 |

---

## 1. Background & Motivation

### 현재 상태
- 대시보드 구성: `MarketOverview`(5카드 그리드: S&P500, Nasdaq, Dow, VIX, USD/KRW) → `MarketMovers`(급등락) + `MarketNews`(뉴스)
- 지수(S&P500 등)와 환율(USD/KRW)이 동일 그리드에 섞여 있어 성격이 다른 데이터가 구분 없이 나열됨
- BE: `MarketOverviewService`가 `INDEX_SYMBOLS[]` 4개 + `fetchUsdKrw()` 1개를 합쳐 단일 `MarketOverviewResponse`로 반환
- 캐시: `market:overview` 5분 TTL

### 문제점
1. **지수/환율 미구분**: 주가 지수와 환율은 해석 방향이 다른데 (지수 ↑ = 좋음, 환율 ↑ = 원화 약세) 같은 시각적 처리로 혼동
2. **섹터 퍼포먼스 부재**: "오늘 어떤 섹터가 강한지" 정보 없음 — 종목 분석의 맥락이 빠짐
3. **금리/원자재 부재**: 10Y 국채 금리·금·유가 같은 거시 지표가 없어 시장 심리 판단 한계
4. 로드맵 Phase 3 완료 이후 대시보드 확장이 미진행 상태

### 해결 방향
- FE: `MarketOverview`를 `IndicesSection` + `MacroSection`(환율·금리·원자재)으로 분리
- BE: 섹터 퍼포먼스 엔드포인트 신설 + MarketOverviewResponse 구조 확장 (금리·원자재 추가)
- 데이터 소스: FMP `/sector-performance` + Yahoo Finance 기존 quote 재활용

---

## 2. Goals & Non-Goals

### Goals
- 지수(S&P500, Nasdaq, Dow, VIX)와 환율/금리/원자재를 시각적으로 분리
- 11개 GICS 섹터 퍼포먼스 일간 변동률 표시
- 10Y 미국 국채 금리(^TNX) 카드 추가
- 주요 원자재(금 GC=F, WTI유 CL=F) 카드 추가
- 모바일 반응형: 섹터 바 가로 스크롤, 매크로 카드 2열 그리드
- 기존 5분 캐시 전략 유지, 추가 데이터도 동일 TTL

### Non-Goals
- 섹터별 상세 종목 리스트 (클릭 시 종목 드릴다운) — 향후 feature
- 암호화폐 시세
- 실시간 스트리밍 (WebSocket) — 현재 폴링 방식 유지
- 글로벌 지수 (유럽, 아시아) 추가 — 미국 주식 전문 서비스 범위 유지
- 과거 추세 차트 (지수/섹터 히스토리컬)

---

## 3. Data Sources & API

### 3.1 섹터 퍼포먼스

| 옵션 | 엔드포인트 | 무료 제한 | 판정 |
|------|-----------|----------|------|
| **FMP** | `/stable/sector-performance` | 250 req/day (기존 pool 공유) | 1차 |
| **Yahoo Finance** | 개별 섹터 ETF(XLK, XLF 등) quote | 무제한 | fallback |

FMP 섹터 퍼포먼스 API는 단일 호출로 11개 섹터 변동률을 반환하므로 효율적입니다.
fallback으로 섹터 대표 ETF 11종의 Yahoo quote를 사용할 수 있습니다.

**섹터 ETF 매핑** (Yahoo fallback용):

| 섹터 | ETF | 한국어명 |
|------|-----|---------|
| Technology | XLK | 기술 |
| Healthcare | XLV | 헬스케어 |
| Financials | XLF | 금융 |
| Consumer Discretionary | XLY | 임의소비재 |
| Communication Services | XLC | 커뮤니케이션 |
| Industrials | XLI | 산업재 |
| Consumer Staples | XLP | 필수소비재 |
| Energy | XLE | 에너지 |
| Utilities | XLU | 유틸리티 |
| Real Estate | XLRE | 부동산 |
| Materials | XLB | 소재 |

### 3.2 금리 · 원자재

기존 3-tier fallback(Finnhub → Yahoo → TwelveData) 구조를 재활용합니다.

| 데이터 | 심볼 (Yahoo) | 표시명 |
|--------|-------------|--------|
| 10Y 국채 금리 | ^TNX | 10Y Treasury |
| 금 | GC=F | Gold |
| WTI 원유 | CL=F | WTI Oil |

### 3.3 캐시 전략

| 키 | TTL | 내용 |
|----|-----|------|
| `market:overview` | 5분 | 기존 — 지수 + 환율 + **금리·원자재 추가** |
| `market:sectors` | 15분 | 신규 — 섹터 퍼포먼스 (변동 빈도 낮음) |

---

## 4. Feature Requirements

### FR-01: 지수 섹션 분리
- 현재 `MarketOverview`에서 지수 카드(S&P500, Nasdaq, Dow, VIX)를 독립 섹션 "주요 지수"로 분리
- 섹션 제목 라벨 표시

### FR-02: 매크로 섹션 신설
- "환율 · 금리 · 원자재" 섹션으로 USD/KRW, 10Y 국채, 금, WTI를 그룹화
- 섹션 제목 라벨 표시
- 카드 스타일은 기존 `IndexCard`와 동일하되, 단위 표시 차별화 (%, ₩, $)

### FR-03: 섹터 퍼포먼스 위젯
- BE: `/api/v1/market/sectors` 엔드포인트 신설
- 11개 GICS 섹터의 일간 변동률 표시
- FE: 가로 스크롤 바 차트 (변동률 기준 정렬, 양수=초록/음수=빨강)
- 모바일: 가로 스크롤 가능, 데스크탑: 전체 표시

### FR-04: BE MarketOverviewResponse 확장
- 기존 `indices` + `usdKrw` 구조에 `macro` 필드 추가 (금리·원자재 배열)
- 또는 `indices` / `forex` / `commodities` 로 분리된 구조
- 하위 호환성: 기존 FE 코드가 깨지지 않도록 점진적 확장

### FR-05: 대시보드 레이아웃 재구성
- 순서: 주요 지수 → 섹터 퍼포먼스 → 환율·금리·원자재 → 급등락 + 뉴스
- 지수와 매크로 사이에 섹터 바를 배치하여 "시장 → 섹터 → 거시경제" 흐름 형성

---

## 5. UI Design

### 5.1 데스크탑 레이아웃 (≥1024px)

```
┌──────────────────────────────────────────────────┐
│ 📊 시장 현황                                       │
├──────────────────────────────────────────────────┤
│ ▸ 주요 지수                                        │
│ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ │
│ │ S&P 500 │ │ Nasdaq  │ │ Dow     │ │ VIX     │ │
│ └─────────┘ └─────────┘ └─────────┘ └─────────┘ │
├──────────────────────────────────────────────────┤
│ ▸ 섹터 퍼포먼스                                     │
│ ┌────┬────┬────┬────┬────┬────┬────┬────┬──┬──┬──┐│
│ │Tech│Hlth│Fin │Disc│Comm│Ind │Stpl│Enrg│Ut│RE│Mt││
│ │+1.2│+0.8│+0.5│-0.1│-0.3│+0.2│-0.1│+1.5│..│..│..││
│ └────┴────┴────┴────┴────┴────┴────┴────┴──┴──┴──┘│
├──────────────────────────────────────────────────┤
│ ▸ 환율 · 금리 · 원자재                               │
│ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ │
│ │ USD/KRW │ │10Y Treas│ │ Gold    │ │ WTI Oil │ │
│ └─────────┘ └─────────┘ └─────────┘ └─────────┘ │
├──────────────────────────────────────────────────┤
│ ┌───────────────────┐ ┌───────────────────┐      │
│ │ 급등/급락 종목     │ │ 시장 뉴스          │      │
│ └───────────────────┘ └───────────────────┘      │
└──────────────────────────────────────────────────┘
```

### 5.2 모바일 레이아웃 (<640px)

- 지수: 2열 그리드 (2×2)
- 섹터: 가로 스크롤 (flex-nowrap overflow-x-auto)
- 매크로: 2열 그리드 (2×2)
- 급등락/뉴스: 1열 스택

---

## 6. Implementation Steps

| Step | 범위 | 작업 |
|------|------|------|
| 1 | BE | `SectorPerformanceService` + `SectorPerformance` record + FmpClient.sectorPerformance() |
| 2 | BE | `MarketController.sectors()` 엔드포인트 + Redis 15분 캐시 |
| 3 | BE | `MarketOverviewResponse` 확장 — macro 필드 추가 (금리·원자재) |
| 4 | BE | `MarketOverviewService` 확장 — 금리(^TNX), 원자재(GC=F, CL=F) 조회 추가 |
| 5 | FE | `types/market.ts` 확장 — SectorPerformance, MacroItem 타입 추가 |
| 6 | FE | `MarketOverview` 리팩터 — 지수 섹션 + 매크로 섹션 분리 |
| 7 | FE | `SectorPerformance` 위젯 컴포넌트 신설 (가로 바 차트) |
| 8 | FE | `useSectorPerformance` React Query 훅 |
| 9 | FE | `MarketDashboard` 레이아웃 재구성 (지수→섹터→매크로→급등락+뉴스) |
| 10 | 검증 | `make web-check` + `make api-check` + 브라우저 확인 |

**예상 기간**: 3~5일
**예상 변경**: BE 6~8 파일, FE 8~10 파일, +800~1200 lines

---

## 7. Risk & Mitigation

| 리스크 | 영향 | 대책 |
|--------|------|------|
| FMP 250 req/day 한도에 섹터 API 추가 부담 | Medium | 15분 캐시로 하루 최대 96회 호출 — 기존 overview 부담과 합산해도 여유 |
| Yahoo Finance 원자재 심볼(GC=F, CL=F) 미지원 | Low | TwelveData fallback 또는 FMP commodity endpoint |
| 섹터 ETF 매핑 정확도 | Low | GICS 표준 매핑, SPDR 공식 ETF 사용 |
| 대시보드 로딩 시간 증가 | Medium | 섹터와 매크로는 별도 React Query로 병렬 로딩, skeleton 분리 |

---

## 8. Acceptance Criteria

| AC | 기준 |
|----|------|
| AC-01 | 지수 카드(S&P500, Nasdaq, Dow, VIX)가 "주요 지수" 섹션에 그룹화되어 표시 |
| AC-02 | USD/KRW, 10Y 국채, 금, WTI가 "환율·금리·원자재" 섹션에 그룹화되어 표시 |
| AC-03 | 11개 GICS 섹터의 일간 변동률이 가로 바 차트로 표시 |
| AC-04 | 모바일(375px)에서 섹터 바가 가로 스크롤 가능 |
| AC-05 | 섹터 API 실패 시 섹터 위젯만 숨김, 나머지 정상 표시 (graceful degradation) |
| AC-06 | 원자재/금리 API 실패 시 해당 카드만 숨김, 나머지 정상 표시 |
| AC-07 | `make web-check` + `make api-check` 통과 |

---

## 9. Dependencies

- **FMP API Key**: 기존 설정 재활용 (`FMP_API_KEY`)
- **@floating-ui/react**: 이미 설치됨 (InfoTooltip)
- **신규 패키지**: 없음 (Tailwind 기존 클래스로 UI 구현)

---

## 10. Out of Scope Decisions

| 결정 | 이유 |
|------|------|
| 섹터 클릭 → 종목 드릴다운 | 별도 feature로 분리 (섹터별 종목 탐색) |
| 지수/섹터 히스토리컬 차트 | 캔들 DB 확장 필요, 현재 스코프 초과 |
| 10Y/2Y 스프레드 계산 | 2Y 데이터 소스 추가 필요, 향후 과제 |
| 환율 변동률(%) 표시 | 환율은 절대 변동(원)이 직관적 — 현재 방식 유지 |
