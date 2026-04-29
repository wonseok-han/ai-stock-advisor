# Design: SEC Filing Integration

> Plan 참조: `docs/01-plan/features/sec-filing-integration.plan.md`

---

## 1. 개요

SEC EDGAR 공개 API(무료, key 불필요)를 통해 미국 상장사의 **8-K 공시**와 **XBRL 재무 팩트**를 수집하여 AI 시그널 RAG 컨텍스트(`ContextAssembler`)에 주입한다. FE 변경 없음, BE 전용.

---

## 2. 아키텍처

```
SecEdgarClient (WebFlux)
    ├── getCik(ticker)           → company_tickers.json 파싱
    ├── getSubmissions(cik)      → submissions/{CIK}.json
    └── getCompanyFacts(cik)     → api/xbrl/companyfacts/{CIK}.json

SecFilingService
    ├── getRecentFilings(ticker, limit)   → List<SecFiling>
    └── getFinancials(ticker)             → SecFinancials

ContextAssembler  (기존 assemble() 에 secService 추가)
    └── Future<List<SecFiling>> → ctx.put("sec_filings", ...)
    └── Future<SecFinancials>   → ctx.put("sec_financials", ...)
```

---

## 3. 도메인 모델

### 3.1 SecFiling

```java
// sec/domain/SecFiling.java
public record SecFiling(
    String ticker,
    String form,         // "8-K", "10-Q" 등
    String title,        // 공시 제목
    LocalDate filedAt,
    String eventCategory, // 한국어 분류
    int daysAgo
) {}
```

### 3.2 SecFinancials

```java
// sec/domain/SecFinancials.java
public record SecFinancials(
    String ticker,
    List<QuarterValue> revenueHistory,    // 최근 4분기
    List<QuarterValue> netIncomeHistory,
    String unit                            // "USD" or "billion USD"
) {
    public record QuarterValue(String quarter, double value) {}
}
```

---

## 4. SEC EDGAR API 명세

### 4.1 Ticker → CIK 매핑

```
GET https://www.sec.gov/files/company_tickers.json
응답: {"0": {"cik_str": 320193, "ticker": "AAPL", "title": "..."}, ...}
처리: 전체 맵을 인메모리에 로드 (앱 기동 시 또는 첫 요청 시 lazy 로드)
캐시: 인메모리 ConcurrentHashMap (재시작 전까지 영구)
```

### 4.2 공시 목록 조회

```
GET https://data.sec.gov/submissions/CIK{10자리 제로패딩}.json
예: CIK 320193 → CIK0000320193.json

응답 구조 (filings.recent):
{
  "filings": {
    "recent": {
      "form": ["8-K", "10-Q", "8-K", ...],
      "filingDate": ["2025-04-23", ...],
      "primaryDocument": ["0000320193-25-000050-index.htm", ...],
      "items": ["2.02", "9.01", ...]    // 8-K item 코드
    }
  }
}
```

### 4.3 XBRL 재무 팩트

```
GET https://data.sec.gov/api/xbrl/companyfacts/CIK{10자리}.json

타겟 경로:
facts.us-gaap.Revenues.units.USD[]         (Revenue, 분기별)
facts.us-gaap.NetIncomeLoss.units.USD[]    (Net Income, 분기별)

각 항목: {"end": "2025-03-31", "val": 95359000000, "form": "10-Q", "frame": "CY2025Q1I", ...}
```

### 4.4 Rate Limit 및 헤더

| 항목 | 값 |
|-----|-----|
| Rate limit | 10 req/s |
| User-Agent | `Nowini/1.0 oshan1112@gmail.com` |
| Accept | `application/json` |
| 에러 처리 | 429 → 1s 대기 후 1회 재시도, 그 외 실패 시 null 반환 |

---

## 5. SecEdgarClient 상세 설계

```java
// sec/infra/SecEdgarClient.java

@Component
public class SecEdgarClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final String BASE = "https://data.sec.gov";
    private static final String TICKERS_URL = "https://www.sec.gov/files/company_tickers.json";
    private static final String USER_AGENT = "Nowini/1.0 oshan1112@gmail.com";

    private final WebClient webClient;
    // ticker → cik 인메모리 캐시 (ConcurrentHashMap)
    private volatile Map<String, String> tickerCikMap;

    // CIK 조회 (인메모리 캐시 우선, 없으면 EDGAR 로드)
    public Optional<String> getCik(String ticker) { ... }

    // 공시 목록 (submissions JSON 원본 반환)
    public Map<String, Object> getSubmissions(String cik) { ... }

    // XBRL 재무 팩트 (companyfacts JSON 원본 반환)
    public Map<String, Object> getCompanyFacts(String cik) { ... }
}
```

**WebClient 설정:**
- `connectTimeout`: 3s
- `readTimeout`: 5s
- `User-Agent` 헤더 기본값 설정
- WebFlux blocking 호출: `block(Duration.ofSeconds(5))` → virtual thread 친화

---

## 6. SecFilingService 상세 설계

### 6.1 getRecentFilings

```java
public List<SecFiling> getRecentFilings(String ticker, int limit) {
    // 1. Redis 캐시 조회 (key: "sec:filings:{ticker}")
    // 2. 캐시 미스 → getCik → getSubmissions → 8-K 필터링 → 이벤트 분류
    // 3. Redis 저장 (TTL: 1h)
    // 4. CIK 없거나 실패 → 빈 리스트 반환 (graceful fallback)
}
```

### 6.2 getFinancials

```java
public SecFinancials getFinancials(String ticker) {
    // 1. Redis 캐시 조회 (key: "sec:financials:{ticker}")
    // 2. 캐시 미스 → getCik → getCompanyFacts → Revenue/NetIncome 추출
    // 3. 최근 4분기 필터 (form=10-Q, 중복 제거, 날짜 역순 정렬)
    // 4. Redis 저장 (TTL: 6h)
}
```

### 6.3 8-K 이벤트 코드 → 한국어 분류 매핑

| Item 코드 | 카테고리 (한국어) |
|-----------|-----------------|
| 1.01 | 중요 계약 체결 |
| 1.02 | 계약 종료 |
| 1.03 | 파산/청산 |
| 2.01 | 중요 자산 취득/처분 |
| 2.02 | 실적 발표 |
| 2.03 | 부채/금융약정 |
| 2.05 | 구조조정/비용 |
| 2.06 | 자산가치 손상 |
| 3.01 | 상장 폐지 위험 |
| 4.01 | 감사인 변경 |
| 5.01 | 경영권 변경 |
| 5.02 | 임원 교체 |
| 5.03 | 정관 변경 |
| 5.07 | 주주총회 결의 |
| 7.01 | FD 규정 공시 |
| 8.01 | 기타 이벤트 |
| 9.01 | 재무제표 및 첨부 |
| (없음) | 기타 공시 |

---

## 7. ContextAssembler 변경

### 7.1 변경 전 / 후

```java
// 변경 전 (5개 Future)
Future<StockProfile> pF = ...
Future<Quote> qF = ...
Future<IndicatorSnapshot> iF = ...
Future<List<NewsItem>> nF = ...
Future<AnalystEstimates> aF = ...

// 변경 후 (7개 Future — sec 2개 추가)
Future<List<SecFiling>> sfF = ex.submit(() -> safely(() -> secService.getRecentFilings(ticker, 5)));
Future<SecFinancials> sxF = ex.submit(() -> safely(() -> secService.getFinancials(ticker)));

ctx.put("sec_filings", secFilingsOf(await(sfF)));
ctx.put("sec_financials", secFinancialsOf(await(sxF)));
```

### 7.2 컨텍스트 JSON 예시 (AAPL)

```json
"sec_filings": [
  {
    "form": "8-K",
    "title": "Earnings Release Q2 FY2025",
    "filed_at": "2025-05-01",
    "event_category": "실적 발표",
    "days_ago": 3
  },
  {
    "form": "8-K",
    "title": "Entry into a Material Definitive Agreement",
    "filed_at": "2025-04-15",
    "event_category": "중요 계약 체결",
    "days_ago": 19
  }
],
"sec_financials": {
  "revenue_trend": [119.6, 124.3, 95.4, 117.9],
  "net_income_trend": [23.4, 24.1, 14.7, 21.8],
  "quarters": ["2024-Q3", "2024-Q4", "2025-Q1", "2025-Q2"],
  "unit": "billion USD"
}
```

---

## 8. 프롬프트 업데이트 (`ai-signal.system.txt`)

`long_term` 섹션 분석 가이드라인에 다음 내용 추가:

```
- sec_filings 가 있으면: 최근 8-K 이벤트(실적 발표, 임원 교체, 중요 계약 등)를
  장기 관점 분석에 반영. 출력 텍스트에 "SEC 공시" 또는 "공시 내용" 표현 사용.
- sec_financials 가 있으면: revenue_trend, net_income_trend 추세를
  매출·순이익 성장성 분석에 활용.
- 두 필드 모두 null 이면 기존 방식대로 분석.
- SEC 공시는 공식 정보이지만 투자 자문이 아님을 유지.
```

---

## 9. Redis 캐시 키 컨벤션

| 키 | TTL | 설명 |
|----|-----|------|
| `sec:filings:{ticker}` | 1h | 8-K 최신 5건 |
| `sec:financials:{ticker}` | 6h | XBRL Revenue/NetIncome |
| (인메모리) `tickerCikMap` | 앱 재시작까지 | Ticker→CIK 전체 맵 |

---

## 10. 에러 처리 전략

| 케이스 | 처리 |
|--------|------|
| CIK 없음 (외국 주식, ETF 등) | 즉시 null 반환, 로그 debug |
| EDGAR 네트워크 오류 (timeout) | null 반환, 기존 컨텍스트로 AI 분석 계속 |
| EDGAR 429 Too Many Requests | 1s sleep 후 1회 재시도, 실패 시 null |
| XBRL 팩트 없음 (신규 상장 등) | SecFinancials 반환하되 빈 리스트 |
| `company_tickers.json` 로드 실패 | 빈 Map으로 초기화, 다음 요청 시 재시도 |

---

## 11. 구현 파일 목록

### 신규 생성

| 파일 | 설명 |
|------|------|
| `sec/domain/SecFiling.java` | 공시 도메인 레코드 |
| `sec/domain/SecFinancials.java` | 재무 팩트 도메인 레코드 |
| `sec/infra/SecEdgarClient.java` | EDGAR API WebClient |
| `sec/service/SecFilingService.java` | 캐시 + 파싱 서비스 |

### 수정

| 파일 | 변경 내용 |
|------|---------|
| `ai/service/ContextAssembler.java` | SecFilingService 주입 + Future 2개 추가 |
| `resources/prompts/ai-signal.system.txt` | long_term 섹션 SEC 공시 가이드라인 추가 |

---

## 12. 비기능 요구사항

| 항목 | 기준 |
|------|------|
| SEC 조회 타임아웃 | 5s (초과 시 null fallback) |
| AI 응답 지연 영향 | 0 — 기존 병렬 Future와 동시 실행 |
| Rate limit 준수 | 동일 ticker 반복 호출 시 Redis 캐시가 EDGAR 직접 호출 차단 |
| 면책 원칙 | 프롬프트에 "공식 정보이나 투자 자문 아님" 유지 |
