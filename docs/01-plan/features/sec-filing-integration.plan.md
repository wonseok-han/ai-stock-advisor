# Plan: SEC Filing Integration

## Executive Summary

| 관점 | 내용 |
|------|------|
| **Problem** | AI 분석 컨텍스트가 기술 지표·뉴스·애널리스트 추정치 위주라, 기업이 직접 공시한 실적·이벤트 데이터(SEC 공시)가 빠져 있어 분석의 공신력과 깊이에 한계가 있음 |
| **Solution** | SEC EDGAR 공개 API(무료, key 불필요)로 8-K·XBRL 재무 팩트를 수집해 RAG 컨텍스트에 주입, Gemini가 공시 기반 근거로 시그널을 생성하도록 개선 |
| **Function UX Effect** | 실적 발표·주요 이벤트(합병·임원 교체 등)·재무 트렌드가 AI 시그널 근거에 명시적으로 반영되어 사용자가 "왜 이 시그널인가"를 더 신뢰하게 됨 |
| **Core Value** | 투자 자문이 아닌 **정보 제공** 원칙을 유지하면서도, 공식 공시 기반 분석으로 서비스 차별성 확보 |

---

## 1. 배경 및 목적

### 1.1 현재 ContextAssembler 구성

```
ticker
profile        (StockProfileService — 기업 개요)
quote          (QuoteService — 현재가·변동)
indicators     (IndicatorService — MACD/Bollinger/RSI/MA)
recent_news    (NewsService — 최근 5건, Finnhub)
analyst_estimates (AnalystEstimatesService — 컨센서스·목표가·EPS)
```

### 1.2 부족한 부분

- **기업 자체 공시 없음**: Finnhub 뉴스는 제3자 기사이지 기업의 공식 공시가 아님
- **실적 히스토리 단조로움**: 최근 분기 EPS 정도만 있고 Revenue 추세·부채 구조 부재
- **이벤트 감지 불가**: 합병·임원 교체·자사주 매입 등 중요 8-K 이벤트 미반영

### 1.3 목표

1. SEC EDGAR API를 통해 **8-K 최신 공시 5건** RAG 컨텍스트에 추가
2. XBRL 팩트에서 **Revenue / Net Income 4분기 추세** 추가
3. 기존 `ContextAssembler.assemble()` 병렬 로딩 패턴에 자연스럽게 통합
4. FE 변경 없음 — BE RAG 컨텍스트 강화만

---

## 2. SEC EDGAR API 개요

### 2.1 사용 엔드포인트 (모두 무료)

| 엔드포인트 | 용도 |
|-----------|------|
| `https://www.sec.gov/files/company_tickers.json` | Ticker → CIK 전체 매핑 (1회 로드) |
| `https://data.sec.gov/submissions/CIK{10자리}.json` | 기업 공시 목록 (8-K/10-Q 포함) |
| `https://data.sec.gov/api/xbrl/companyfacts/CIK{10자리}.json` | XBRL 재무 팩트 (Revenue, NetIncome 등) |

### 2.2 Rate Limit & 요구사항

- 10 req/s 상한
- `User-Agent: {앱명} {이메일}` 헤더 필수 (EDGAR 정책)
- 미국 주식만 해당 (해외 종목 CIK 없음 → graceful fallback)

---

## 3. 기능 범위 (Scope)

### In Scope

| # | 기능 | 설명 |
|---|------|------|
| F-01 | Ticker → CIK 조회 | `company_tickers.json` 로딩 + 인메모리 캐시 |
| F-02 | 8-K 최신 5건 수집 | 제목·제출일·주요 이벤트 코드 |
| F-03 | XBRL Revenue/NetIncome | 최근 4분기 추세 (us-gaap 기준) |
| F-04 | Redis 캐시 적용 | 8-K: TTL 1h, XBRL: TTL 6h, CIK: 영구 |
| F-05 | ContextAssembler 통합 | 기존 병렬 로딩 패턴에 `sec_filings` 필드 추가 |
| F-06 | 한국어 이벤트 분류 | 8-K item 코드 → 한국어 카테고리 매핑 |

### Out of Scope

- 10-K 전문 파싱 (텍스트 분량 과다, 토큰 초과 위험)
- DEF 14A (의결권) 공시
- 외국 사기업 / 미국 이외 거래소 종목
- FE 표시 (이번 iteration은 RAG 컨텍스트 강화만)

---

## 4. 기술 설계 방향

### 4.1 신규 컴포넌트

```
sec/
├── domain/
│   ├── SecFiling.java          (record: ticker, form, title, filedAt, items)
│   └── SecFinancials.java      (record: ticker, revenueHistory, netIncomeHistory)
├── infra/
│   └── SecEdgarClient.java     (WebFlux WebClient, 비동기)
└── service/
    └── SecFilingService.java   (CIK 조회 + 8-K + XBRL 조합)
```

### 4.2 ContextAssembler 변경

```java
// 기존 assemble() 에 SecFilingService 주입
Future<SecFiling[]> sF = ex.submit(() -> safely(() -> secService.getRecentFilings(ticker, 5)));
ctx.put("sec_filings", secFilingsOf(await(sF)));
```

- 실패 시 `null` → 프롬프트에서 해당 섹션만 생략 (기존 패턴 동일)

### 4.3 프롬프트 컨텍스트 추가 형태

```json
"sec_filings": [
  {
    "form": "8-K",
    "title": "Earnings Release Q1 2025",
    "filed_at": "2025-04-23",
    "event_category": "실적 발표",
    "days_ago": 5
  }
],
"sec_financials": {
  "revenue_trend": [92.3, 98.1, 117.9, 124.3],
  "net_income_trend": [14.7, 16.2, 19.1, 21.8],
  "unit": "billion USD",
  "quarters": ["2024-Q2", "2024-Q3", "2024-Q4", "2025-Q1"]
}
```

---

## 5. 구현 순서

| Step | 작업 | 비고 |
|------|------|------|
| 1 | `SecEdgarClient` 구현 (WebFlux) | CIK 조회 + submissions + companyfacts |
| 2 | `SecFilingService` — 8-K 파싱 + 이벤트 코드 분류 | item 코드 매핑 테이블 |
| 3 | `SecFinancials` — XBRL Revenue/NetIncome 추출 | 최근 4분기 필터링 |
| 4 | Redis 캐시 통합 | CacheService 활용 |
| 5 | `ContextAssembler` 통합 | 기존 병렬 Future 패턴 |
| 6 | `PromptBuilder` 업데이트 | sec_filings / sec_financials 섹션 추가 |
| 7 | 통합 테스트 (AAPL/MSFT 실데이터 기준) | 수동 검증 |

---

## 6. 비기능 요구사항

| 항목 | 요구사항 |
|------|---------|
| 응답 지연 | SEC 조회 실패해도 기존 AI 응답 지연 없이 graceful fallback |
| Rate Limit | WebClient에 10 req/s 제한 (Bucket4j 또는 간단한 Semaphore) |
| 에러 처리 | CIK 없는 종목(외국 주식 등) → `sec_filings: null` 로 무시 |
| 면책 원칙 | SEC 공시는 "참고용 정보" — 투자 자문 표현 금지 원칙 유지 |

---

## 7. 성공 기준

- [ ] AAPL, MSFT, NVDA 등 주요 종목에서 `sec_filings` 컨텍스트 정상 조립
- [ ] 8-K 이벤트 카테고리 한국어 분류 정확도 확인
- [ ] XBRL Revenue 4분기 추세 정상 추출
- [ ] 기존 AI 응답 시간 유지 (SEC 조회 타임아웃 ≤ 3s, 실패 시 즉시 fallback)
- [ ] CI 금칙어 스캔 통과

---

## 8. 관련 파일

- `apps/api/src/main/java/com/nowini/ai/service/ContextAssembler.java`
- `apps/api/src/main/java/com/nowini/ai/service/PromptBuilder.java`
- `apps/api/src/main/java/com/nowini/cache/CacheService.java`
