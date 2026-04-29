# SEC Filing Integration — Design vs Implementation Gap Analysis

## Analysis Overview

| Item | Value |
|------|-------|
| Feature | sec-filing-integration |
| Design Doc | `docs/02-design/features/sec-filing-integration.design.md` |
| Analysis Date | 2026-04-28 |
| **Overall Match Rate** | **96%** |

> **참고**: 본 Design 은 §1, §11 에서 명시적으로 "BE 전용, FE 변경 없음" 으로 한정. 이후 추가된 FE UI 레이어(엔드포인트 노출 + 패널 컴포넌트)는 Design 범위 외 추가 기능으로, BE 매칭률에는 영향 없음.

## Score Summary

| Category | Score | Status |
|----------|:-----:|:------:|
| Domain Model (§3) | 100% | ✅ |
| EDGAR API Spec (§4) | 100% | ✅ |
| SecEdgarClient (§5) | 95% | ✅ |
| SecFilingService (§6) | 92% | ✅ |
| ContextAssembler (§7) | 95% | ✅ |
| Prompt Update (§8) | 90% | ✅ |
| Redis Cache Convention (§9) | 100% | ✅ |
| FE Layer (Design 범위 외, 추가 기능) | N/A | ➕ |

---

## Section-by-Section Verification

### §3 Domain Model (100%)

`SecFiling` / `SecFinancials` 레코드 필드 및 타입 완전 일치.

### §4 EDGAR API Spec (100%)

| Item | Design | Implementation | Match |
|------|--------|----------------|:-----:|
| Tickers URL | `https://www.sec.gov/files/company_tickers.json` | 동일 | ✅ |
| Submissions URL pattern | `CIK{10자리}.json` | `CIK{cik}.json` | ✅ |
| CompanyFacts URL | `/api/xbrl/companyfacts/CIK{10자리}.json` | 동일 | ✅ |
| User-Agent | `Nowini/1.0 oshan1112@gmail.com` | 동일 | ✅ |
| Timeout | 5s | `Duration.ofSeconds(5)` | ✅ |
| 429 처리 | 1s sleep + 1회 재시도 | 동일 | ✅ |
| connectTimeout | 3s 분리 명시 | 5s 통합 | ⚠️ minor |

### §5 SecEdgarClient (95%)

`getCik`, `getSubmissions`, `getCompanyFacts` 시그니처 완전 일치. 인메모리 ConcurrentHashMap + double-checked locking lazy 로드 구현. CIK 10자리 zero-pad 적용.

**Minor Gap**: Design이 connectTimeout=3s / readTimeout=5s 로 분리 명시했으나, 구현은 5s 통합 (`SecEdgarClient.java:51-54`).

### §6 SecFilingService (92%)

| Item | Status |
|------|:------:|
| `getRecentFilings` Redis 우선 | ✅ |
| `getFinancials` Redis 우선 | ✅ |
| 8-K 필터 | ✅ |
| 17개 Item 코드 한국어 매핑 | ✅ |
| `daysAgo` 계산 | ✅ |
| 단위 billion USD 변환 | ✅ |
| Revenue fallback (`RevenueFromContractWith...`) | ➕ 합리적 보강 |
| Form filter `10-K` 추가 | ➕ 합리적 보강 |
| 8-K title 소스 (`primaryDocument` 파일명) | ⚠️ 가독성 저하 |
| 분류명 표기 슬래시→가운뎃점 | ⚠️ minor |

### §7 ContextAssembler (95%)

`sec_filings` / `sec_financials` Future 2개 추가 완료. `secFilingsOf` / `secFinancialsOf` 헬퍼 구현. 모든 컨텍스트 JSON 필드명 일치.

### §8 Prompt Update (90%)

`long_term` 섹션에 `sec_filings` / `sec_financials` 가이드라인 추가 완료. "공식 정보이나 투자 자문 아님" 별도 SEC 전용 문구는 미추가 (전역 면책 규칙으로 흡수).

### §9 Redis Cache Keys (100%)

| Key | Design TTL | Implementation | Match |
|-----|:----------:|:------------:|:-----:|
| `sec:filings:{ticker}` | 1h | `Duration.ofHours(1)` | ✅ |
| `sec:financials:{ticker}` | 6h | `Duration.ofHours(6)` | ✅ |

---

## FE Layer Additions (Design 범위 외, 추가 기능)

Design §1, §11 은 BE 전용으로 한정했으나, 사용자 가시성을 높이기 위해 FE UI 레이어가 추가됨. 모두 프로젝트 컨벤션(`.claude/conventions.md`)에 부합하므로 BE 매칭률 산정에는 포함하지 않고 ➕ 보강 항목으로 기록.

### 추가 항목

| 영역 | 위치 | 설명 |
|------|------|------|
| REST 엔드포인트 | `StockController.java:126-130` | `GET /api/v1/stocks/{ticker}/sec-filings` — `SecFilingService.getRecentFilings(ticker, 5)` 위임. Ticker 정규식 검증. |
| TS 타입 | `apps/web/src/types/stock.ts:143-150` | `SecFiling` (BE record 1:1 매핑) |
| API 클라이언트 | `apps/web/src/lib/api/stocks.ts:46-48` | `getSecFilings(ticker)` — `apiFetch` 사용 |
| React Query 훅 | `apps/web/src/features/stock-detail/sec-filings/hooks/use-sec-filings.ts` | `staleTime: 1h` (BE Redis TTL과 정렬), `retry: 1` |
| 패널 컴포넌트 | `apps/web/src/features/stock-detail/sec-filings/sec-filings-panel.tsx` | 8-K 타임라인, 카테고리 색상 분기(긍정/부정/중립), EDGAR 외부 링크, 면책 문구 노출 |
| 통합 | `apps/web/src/features/stock-detail/stock-detail-view.tsx:33` | `AiSignalPanel` 다음 / `NewsPanel` 이전에 배치 |

### FE 컨벤션 준수 검증

| 항목 | 결과 |
|------|:----:|
| 파일명 kebab-case | ✅ `use-sec-filings.ts`, `sec-filings-panel.tsx` |
| 컴포넌트 PascalCase | ✅ `SecFilingsPanel`, `FilingRow` |
| 훅 camelCase | ✅ `useSecFilings` |
| 3-layer (Component → Hook → API) | ✅ |
| 절대경로 import + 외부/내부 분리 | ✅ |
| 면책 문구 노출 | ✅ "참고용 정보이며 투자 판단의 근거로 사용하지 마세요" |
| 캐시 정렬 | ✅ FE staleTime 1h ↔ BE Redis 1h |

### FE 합리적 보강 사항

- **카테고리 색상 분기**: NEGATIVE/POSITIVE 분류로 시각적 구분 — Design 명세 외, UX 개선.
- **EDGAR 외부 링크**: `?CIK={ticker}` 사용 (SEC가 ticker 자동 인식).
- **빈 응답 graceful 처리**: `error || !data || data.length === 0` 시 패널 자체 렌더링 생략.
- **Financials는 노출 안 함**: 정책상 AI 컨텍스트로만 활용 (UI 직접 노출 X) — 합리적 결정.

---

## Added Features (Design X, Implementation O)

| 보강 항목 | 위치 | 근거 |
|----------|------|------|
| `RevenueFromContractWithCustomerExcludingAssessedTax` fallback | `SecFilingService.java:120` | ASC 606 이후 신규 텍사노미 (Apple/Tesla 등) |
| Form filter `10-K` 추가 | `SecFilingService.java:146` | 연간 보고서 4분기 데이터 누락 방지 |
| 테스트 helper 메서드 | `SecEdgarClient.java:202~` | 단위테스트 인프라 |
| **FE UI 레이어 전체** | `apps/web/src/features/stock-detail/sec-filings/` 등 | 사용자 가시성, 면책 문구 노출 강화 (Design 후속 추가) |
| **REST 엔드포인트 `/sec-filings`** | `StockController.java:126` | FE 데이터 공급용 |

---

## Recommended Actions

### 즉시 조치 불필요 (96% ≥ 90%)

### 선택적 개선 사항

1. **8-K title 품질 개선** (Medium): `primaryDocument` 파일명 대신 `eventCategory` 기반 한글 title 구성.
   - 예: `"8-K 공시 — 실적 발표"` (item=2.02)
   - 위치: `SecFilingService.java:93`
   - FE 패널은 현재 `eventCategory` 만 표시 — title 개선 후 패널에 부가 노출 가능.

2. **connectTimeout 분리** (Low): connect 3s / response 5s 로 Design 명세와 정렬.
   - 위치: `SecEdgarClient.java:51`

3. **Design 문서 동기화** (Low): 후속 분석에서 100% 매칭을 위해 아래 항목 반영 권장.
   - §6.3 분류명 표기 (`/` → `·`)
   - Revenue fallback 및 `10-K` form 처리 내용 추가
   - §13(신규) FE UI 레이어 명세 추가 (엔드포인트 + 패널 컴포넌트 + 카테고리 색상 매핑)
