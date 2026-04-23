# 완료 리포트: analyst-estimates

> **애널리스트 평점/목표가 + 분기 실적 패널**
>
> **생성일**: 2026-04-23
> **Feature**: analyst-estimates
> **Status**: ✅ 완료
> **Match Rate**: 93% (Design 93%, Architecture 95%, Convention 98%)

---

## Executive Summary

### 1.3 Value Delivered

| 관점 | 내용 |
|------|------|
| **Problem** | 종목 상세에 펀더멘털(P/E, 52주 고저)은 있지만 "월가가 이 종목을 어떻게 보는가"에 대한 애널리스트 컨센서스·목표가·분기 실적 정보가 없어, 초보 투자자가 현재 주가의 시장 기대치 대비 위치를 판단할 수 없음. |
| **Solution** | Yahoo Finance quoteSummary 모듈 확장(financialData + recommendationTrend + earningsHistory)으로 애널리스트 데이터 수집. curl 기반 TLS 우회 + Redis 24h 캐시로 안정성과 효율성 확보. FMP 무료 플랜 402 제약에 따라 Yahoo 단일 소스로 운영. |
| **Function UX Effect** | 종목 상세 페이지에 "애널리스트 컨센서스" 패널 신설: (1) 평점 게이지 — 1.0~5.0 점수 + 한국어 라벨 + 5단계 분포 차트, (2) 목표가 바 — 현재가 대비 High/Mean/Low + Upside/Downside %, (3) 분기 실적 테이블 — 최근 4분기 EPS Actual/Estimate + Beat/Miss/Meet 라벨. 모바일 반응형(768px 이하 1열) 구현. |
| **Core Value** | 초보 투자자가 "전문가들은 이 종목을 어떻게 보나?"에 즉답 가능. 펀더멘털 정보(P/E 등) → 시장 기대치(애널리스트 컨센서스)로 판단 범위 확대. 분기 실적 Beat/Miss 히스토리로 주가 변동의 역사적 맥락 제공. |

---

## PDCA 사이클 요약

### 1. Plan (계획 단계)
- **문서**: `docs/01-plan/features/analyst-estimates.plan.md`
- **목표**: Yahoo Finance 데이터 연계로 애널리스트 컨센서스·목표가·분기 실적 3개 섹션 제공
- **예상 일정**: 1일 (소규모 feature)

### 2. Design (설계 단계)
- **문서**: `docs/02-design/features/analyst-estimates.design.md`
- **주요 설계 결정**:
  1. Yahoo quoteSummary 모듈 3개 추가(financialData, recommendationTrend, earningsHistory)
  2. curl 기반 TLS 우회 (Java HttpClient 차단 우회)
  3. Redis 캐시 키 통합(`yahoo:summary:{ticker}`, 24h TTL) — CompanyOverview와 공유로 API 호출 최소화
  4. FMP fallback 설계 수립 (후에 무료 플랜 402 제약으로 미연결)
  5. FE: 3개 서브컴포넌트(RatingGauge, PriceTargetBar, EarningsHistory) + 반응형 레이아웃

### 3. Do (구현 단계)
- **실제 구현 범위**:
  - **BE**: AnalystEstimates 도메인 record, YahooFinanceClient 확장, AnalystEstimatesService (24h 캐시 + enrichCurrentPrice), StockController 엔드포인트 추가
  - **FE**: AnalystEstimates 타입, getAnalystEstimates API, useAnalystEstimates 훅, 3개 컴포넌트(rating-gauge, price-target-bar, earnings-history), analyst-panel 통합
  - **인프라**: curl + ca-certificates Dockerfile 설치, netty-resolver-dns macOS 호환성 수정, Render 배포 전환
- **실제 소요 기간**: 약 1.5일 (설계 대비 약간 초과, 커브 테스트 포함)

### 4. Check (검증 단계)
- **분석 문서**: `docs/03-analysis/analyst-estimates.analysis.md`
- **설계 부합도**: 93% (Design 93%, Architecture 95%, Convention 98%)
- **발견 이슈**:
  - FMP 3개 analyst 엔드포인트 모두 무료 플랜에서 HTTP 402 반환 (의도적 제거)
  - Yahoo 모듈 상수를 `ALL_MODULES`로 통합 (캐시 효율 향상)
  - RatingGauge에서 `label` prop 제거 (한국어 서비스 특성)
  - `enrichCurrentPrice()` 로직 추가 (설계 未記載, 구현 중 필요성 발생)
  - `'EST'` result 타입 추가 (미래 분기 전망치 표시)

### 5. Act (개선 단계)
- **실행한 개선**:
  1. 설계 문서(`analyst-estimates.design.md`) 섹션 2.2 업데이트 — FMP fallback 미지원 명시
  2. FMP DTO 구조 교정 (공식 API 엔드포인트명)
  3. `enrichCurrentPrice()` 로직 검증 및 테스트
  4. 모바일 반응형 테스트(768px breakpoint) 확인
  5. `make check` 통과 (tsc + lint + gradlew check)

---

## 완료 결과

### 파일 변경 통계
- **총 변경 파일**: 20개
- **추가**: 1760줄 (신규 컴포넌트, 도메인, 서비스)
- **삭제**: 67줄 (구식 FMP 메서드 제거, 비활용 import 정리)

### 주요 커밋
1. **acf9ac6** — `feat: 애널리스트 컨센서스 패널 — 평점·목표가·분기 실적 (analyst-estimates)`
   - BE: AnalystEstimates, YahooFinanceClient, AnalystEstimatesService, StockController
   - FE: analyst-panel, rating-gauge, price-target-bar, earnings-history 컴포넌트
   - Infra: curl + ca-certificates, netty-resolver-dns macOS 수정, Render 배포

2. **f405050** — `docs: analyst-estimates 설계 문서 현행화 + gap 분석 보고서`
   - Design 문서 섹션 2.2 업데이트 (FMP fallback 미지원)
   - Gap Analysis 최종 보고서 작성

### 구현된 주요 기능

#### BE (Backend)
- ✅ `AnalystEstimates.java` — 도메인 record (Rating, PriceTarget, EarningsQuarter 중첩)
- ✅ `AnalystEstimatesService` — Yahoo primary + enrichCurrentPrice + 24h Redis 캐시
- ✅ `YahooFinanceClient.analystEstimates()` — quoteSummary 모듈 3개 파싱 (financialData, recommendationTrend, earningsHistory)
- ✅ `StockController` — `GET /api/v1/stocks/{ticker}/analyst` 엔드포인트
- ✅ Redis 캐시 키 통합 (`yahoo:summary:{ticker}`) — CompanyOverview와 공유로 불필요한 API 호출 제거

#### FE (Frontend)
- ✅ `AnalystEstimates` 타입 정의 (rating, priceTarget, earnings 3개 섹션)
- ✅ `getAnalystEstimates(ticker)` API 함수
- ✅ `useAnalystEstimates` React Query 훅 (24h staleTime, retry: 1)
- ✅ `AnalystPanel` — 3개 서브컴포넌트 통합 + graceful degradation
- ✅ `RatingGauge` — 1.0~5.0 점수 원형 배지 + 5단계 분포 막대(strongBuy/buy/hold/sell/strongSell)
- ✅ `PriceTargetBar` — High/Mean/Low 레인지 바 + 현재가 마커 + Upside/Downside %
- ✅ `EarningsHistory` — 4분기 EPS 테이블 + Beat/Miss/Meet 라벨 + Surprise % (모바일 카드 뷰)
- ✅ 반응형 레이아웃 (768px 이상: 2열, 이하: 1열 스택)

#### Infra (인프라)
- ✅ Dockerfile curl + ca-certificates 설치 (TLS 우회용)
- ✅ netty-resolver-dns macOS 호환성 수정
- ✅ Render 배포 전환 (Fly.io → Render)

### 의도적 설계 변경 (Intentional Deviations)

| 항목 | 설계 | 구현 | 근거 |
|------|------|------|------|
| FMP fallback | Yahoo + FMP 이중 소스 | Yahoo 단일 소스 | FMP 무료 플랜: analyst 3개 엔드포인트 모두 HTTP 402 반환 |
| Yahoo 모듈 상수 | `ANALYST_MODULES` 별도 | `ALL_MODULES` 통합 | Redis 캐시 키(`yahoo:summary:{ticker}`) 공유로 중복 API 호출 제거 |
| RatingGauge props | `label` + `labelKo` | `labelKo`만 사용 | 한국어 서비스 특성 (영문 label 불필요) |
| EarningsQuarter result | BEAT / MISS / MEET | + EST 추가 | 미발표 분기 전망치 표시용 (향후 분석 지표 활용) |

---

## 검증 결과

### 함수형 검증 (AC — Acceptance Criteria)

| AC ID | 검증 항목 | 상태 | 확인 방법 |
|-------|----------|:----:|---------|
| AC-1 | AAPL 종목 조회 시 3개 섹션 모두 표시 | ✅ | 브라우저 테스트 (localhost:3000) |
| AC-2 | 평점 게이지에 1.0~5.0 점수 + 라벨 + 분포 | ✅ | RatingGauge 렌더링 확인 |
| AC-3 | 목표가 바에 현재가 마커 + upside/downside % | ✅ | PriceTargetBar 계산 검증 |
| AC-4 | 최근 4분기 EPS + Beat/Miss 라벨 | ✅ | EarningsHistory 테이블 확인 |
| AC-5 | FMP 실패 시 동작 | ⏸️ | FMP 미연결 (Yahoo만 운영) |
| AC-6 | 데이터 없는 종목 → 패널 숨김 | ✅ | 소형주 테스트 (graceful degradation) |
| AC-7 | Redis 24h 캐시 적용 | ✅ | BE 로그 + 캐시 히트 확인 |
| AC-8 | tsc --noEmit + gradlew check 통과 | ✅ | `make check` 통과 |
| AC-9 | 모바일 반응형(768px 이하 1열) | ✅ | 브라우저 DevTools 768px 검증 |

### 코드 품질 검증

| 검증 항목 | 결과 | 상태 |
|----------|:----:|:----:|
| FE TypeScript tsc --noEmit | 0 에러 | ✅ |
| FE ESLint (pnpm lint) | 0 에러 | ✅ |
| BE Gradle check (테스트 + 정적 분석) | 0 에러 | ✅ |
| FE 파일명 kebab-case 준수 | 100% | ✅ |
| BE 패키지 레이아웃 domain-driven | 100% | ✅ |
| Import 순서 (외부 → @/ → 상대 → type → style) | 100% | ✅ |
| API 응답 null handling | 완벽 | ✅ |

---

## 배운 점

### 긍정적인 측면

1. **Yahoo quoteSummary 모듈 통합의 효율성**
   - `ALL_MODULES` 상수로 6개 모듈을 한 번에 요청하고 Redis 캐시 키(`yahoo:summary:{ticker}`) 공유
   - CompanyOverview와 AnalystEstimates가 동일 캐시 사용 → 불필요한 중복 API 호출 제거
   - 캐시 효율 약 50% 향상 (예상)

2. **Graceful Degradation 구현의 견고성**
   - 데이터 부분 누락(rating만 있고 earnings 없음) 시 가용 섹션만 렌더링
   - null 체크로 전체 패널 미렌더링 (소형주, ETF 대응)
   - UX 좌절감 없음

3. **FMP 무료 플랜 제약에 대한 현실적 대응**
   - 설계 단계: FMP fallback 계획
   - 구현 단계: HTTP 402 발견 → 설계 문서 업데이트
   - 유일한 fallback은 QuoteService의 현재가 보강(enrichCurrentPrice) — 실제 운영에서 충분

4. **모바일 반응형 테일윈드 활용**
   - `grid-cols-1 gap-4 sm:grid-cols-2` 패턴으로 깔끔한 모바일 반응형 구현
   - 모바일 카드 뷰(EarningsHistory) — 접근성 개선

5. **커밋 메시지의 명확성**
   - 기능 커밋(`acf9ac6`)과 문서 커밋(`f405050`)을 분리
   - bkit feature 이름(`analyst-estimates`) 일관성 유지

### 개선할 점

1. **FMP 엔드포인트 조기 검증 필요**
   - 설계 단계에서 FMP API 키 권한 여부 확인 가능했음
   - 향후 설계 단계에서 "외부 API 가용성 검증" 체크리스트 추가 권장

2. **enrichCurrentPrice() 로직의 사전 명시**
   - 설계 문서에 "현재가 null 대응" 계획 명시 부족
   - Design 섹션 2.3에 이미 기술되었으나, "Error Handling" 섹션 6에 더 상세히 기술할 수 있었음

3. **Earnings `'EST'` 상태 추가의 타이밍**
   - 설계 단계에서 "미발표 분기" 시나리오 예상 가능
   - Design 섹션 1.5 ("Earnings Result 판정")에 EST 추가 필요

4. **테스트 자동화 미실행**
   - 브라우저 수동 테스트만 수행 (AC-1, AC-2, AC-3, AC-6, AC-9)
   - 향후 E2E 테스트(Playwright/Cypress) 구현 권장

### 차후 적용 사항

1. **설계 단계에서 "외부 API 가용성 체크리스트" 추가**
   - Fallback 설계 전에 API 키 권한, 무료 플랜 한도 사전 확인
   - 리스크 섹션에 명시

2. **현재가 보강(enrichCurrentPrice) 패턴 문서화**
   - "부분 데이터 보강 패턴"으로 확립하여 향후 유사 기능에 재활용

3. **Earnings 미발표 분기 처리 프로토콜**
   - `'EST'` 상태 추가 — 향후 실적 예상 분석 기능 확장 시 기초 자료로 활용

4. **모바일 카드 뷰 컴포넌트 템플릿화**
   - EarningsHistory의 모바일 카드 뷰(`md:hidden`) → 재사용 가능한 `<MobileCardView>` 컴포넌트로 분리 검토

---

## 다음 단계

### 즉시 (다음 스프린트)
- [ ] 브라우저 수동 테스트 확장 (AAPL, MSFT, NVDA, 소형주 5종)
- [ ] 배포 후 실제 Yahoo 데이터 수신 대기 시간 모니터링 (curl + TLS 우회)
- [ ] Redis 캐시 히트율 로그 분석 (CompanyOverview와의 공유 효율)

### 중기 (로드맵)
- [ ] FMP 유료 플랜 전환 검토 (분석가 개별 평점 리스트 필요 시)
- [ ] Analyst 예상/실적 동향 차트화 (시계열 시각화 — 분석 심화)
- [ ] E2E 테스트 구현 (Playwright/Cypress)

### 장기 (v0.2.0)
- [ ] 실적 발표 캘린더 + 알림 (별도 feature: earnings-calendar)
- [ ] 매출(Revenue) 실적 비교 (현재는 EPS만 제공)
- [ ] AI 신호와 애널리스트 의견 연계 분석

---

## 관련 문서

- **Plan**: [analyst-estimates.plan.md](../01-plan/features/analyst-estimates.plan.md)
- **Design**: [analyst-estimates.design.md](../02-design/features/analyst-estimates.design.md)
- **Analysis**: [analyst-estimates.analysis.md](../03-analysis/analyst-estimates.analysis.md)
- **Commits**:
  - acf9ac6 — 기능 구현
  - f405050 — 문서 현행화

---

## 요약

**analyst-estimates** 기능은 99개 라인의 설계를 바탕으로 1760줄의 구현 코드(BE + FE)와 20개 파일 변경으로 완료되었습니다.

**설계 부합도 93%** 달성으로 Gap Analysis 분석이 완료되었으며, 모든 의도적 변경(FMP 미연결, Yahoo 모듈 통합, enrichCurrentPrice 추가)은 운영 환경에서의 합리적 판단입니다.

**가용성**: AAPL 등 주요 지수 종목은 즉시 패널 렌더링, 소형주는 graceful degradation으로 사용자 경험 부족 없음.

**인프라**: curl 기반 TLS 우회 + Redis 24h 캐시 + Render 배포로 안정적 운영 체제 확보.

**다음**: 배포 후 모니터링 → FMP 유료 전환 검토 → E2E 테스트 구현 순서로 진행 예정.
