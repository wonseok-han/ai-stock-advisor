# signal-accuracy Design Document

> **Summary**: AI 시그널 방향 정합도(hit rate) 측정 인프라. `ai_signal_audit` + `candles` 를 조합해 **배치 평가 → 집계 API → FE 배지** 를 구성한다.
>
> **Project**: ai-stock-advisor
> **Version**: v0.1.1
> **Author**: wonseok-han
> **Date**: 2026-04-20
> **Status**: Draft
> **Planning Doc**: [signal-accuracy.plan.md](../../01-plan/features/signal-accuracy.plan.md)

---

## 1. Overview

### 1.1 Design Goals

- `ai_signal_audit` 레코드를 **변조하지 않고** (append-only 감사 원칙 유지), 별도 테이블 `ai_signal_evaluation` 으로 평가를 파생 저장.
- 캔들 DB(`candles`) 만 사용 — **외부 API 호출 0** 로 비용·Rate limit 부담 제거.
- 스케줄러·백필이 **idempotent** — 동일 (audit_id, window) 쌍 재계산 시에도 중복 없음.
- FE 는 **집계만** 노출, 종목별 개별 시그널 이력은 비공개 (법적/신뢰도 리스크).

### 1.2 Design Principles

- **Append-only audit + derived evaluation** — 원천 데이터 불변, 해석은 파생 테이블에서.
- **Pure domain logic 분리** — `SignalOutcomeEvaluator` 는 infra 의존 0, 단위 테스트 완전 커버.
- **Sampling threshold** — 샘플 5건 미만 시 UI 숨김·API `sampleSizeSufficient: false`. 통계적 무의미 + 오해 방지.
- **Linguistic guardrail** — "정확도/예측/적중" 금지, "정합도/방향 일치율" 만 사용.

---

## 2. Architecture

### 2.1 Component Diagram

```
┌────────────────────┐
│  @Scheduled (cron) │  매일 1회 06:00 UTC
└─────────┬──────────┘
          │
          ▼
┌───────────────────────────────────────────────┐
│  SignalEvaluationService                      │
│  ─────────────────────────────────────────── │
│  1. audit where generated_at < now - window   │
│     AND not yet evaluated(window)             │
│  2. price_at_signal ← audit.context_payload   │
│     .quote.price  (또는 candle[signal_date])  │
│  3. price_at_window_end ← candle[            │
│     signal_date + window, next business day]  │
│  4. SignalOutcomeEvaluator.evaluate(...)      │
│  5. INSERT INTO ai_signal_evaluation          │
└─────────┬─────────────────────────────────────┘
          │ (batch 1000, TX per batch)
          ▼
┌────────────────────────────────────────┐
│  ai_signal_evaluation  (Postgres)      │
└─────────┬──────────────────────────────┘
          │
          ▼ GET /api/v1/ai/accuracy?window=30
┌────────────────────────────────────────┐
│  SignalAccuracyService                 │
│  ─ Redis 1h cache                      │
│  ─ aggregate(COUNT, SUM, bySignal)     │
└─────────┬──────────────────────────────┘
          │
          ▼
┌────────────────────────────────────────┐
│  AccuracyBadge (FE)                    │
│  "지난 30일 분석 방향 정합도 XX%"       │
└────────────────────────────────────────┘
```

### 2.2 Data Flow

```
[Signal 발행 시] AiSignalService → audit.insert (context_payload.quote.price 저장됨)

[매일 06:00 UTC]
Scheduler
  → audit(generated_at < now-7d, no eval for window=7) 조회
  → candles 에서 end-price 조회
  → evaluator.evaluate() → evaluation.insert
  → (window=30 도 동일)

[FE 렌더 시]
StockDetailPage
  → /api/v1/ai/accuracy?window=30  (전역, ticker 미지정)
  → Redis 1h cache
  → AccuracyBadge 렌더
```

### 2.3 Dependencies

| Component | Depends On | Purpose |
|-----------|-----------|---------|
| `SignalEvaluationScheduler` | `SignalEvaluationService` | 일일 트리거 |
| `SignalEvaluationService` | `AiSignalAuditRepository`, `CandleRepository`, `AiSignalEvaluationRepository`, `SignalOutcomeEvaluator` | 평가 오케스트레이션 |
| `SignalOutcomeEvaluator` | — (pure domain) | Signal→Direction 매핑 + hit 판정 |
| `SignalAccuracyService` | `AiSignalEvaluationRepository`, `RedisCacheAdapter` | 집계 + 캐시 |
| `AdminEvaluationController` | `SignalEvaluationService` | 백필 트리거 |
| `SignalAccuracyController` | `SignalAccuracyService` | 외부 노출 API |
| `AccuracyBadge` (FE) | `useAccuracy` React Query hook | UI 렌더 |

---

## 3. Data Model

### 3.1 Entity Definition

```java
// Domain records (pure, no JPA annotations)
public record SignalOutcome(
        Direction predicted,     // UP / FLAT / DOWN
        Direction actual,
        BigDecimal changePct,
        boolean hit
) {
    public enum Direction { UP, FLAT, DOWN }
}

public record EvaluationWindow(int days) {
    public static final EvaluationWindow W7 = new EvaluationWindow(7);
    public static final EvaluationWindow W30 = new EvaluationWindow(30);
}

public record AccuracySummary(
        int window,
        int sampleSize,
        boolean sampleSizeSufficient,  // >= 5
        BigDecimal hitRate,            // 0.00 ~ 1.00 (null if insufficient)
        Map<Signal, BucketStat> bySignal,
        Instant evaluatedThrough        // 최신 evaluated_at
) {
    public record BucketStat(int count, BigDecimal hitRate) {}
}
```

### 3.2 Entity Relationships

```
[ai_signal_audit]  1 ──── 0..N  [ai_signal_evaluation]
   id (PK)                          audit_id (FK, CASCADE)
                                    window_days (PK 구성 중 하나)
                                    UNIQUE (audit_id, window_days)
```

### 3.3 Database Schema

```sql
-- apps/api/src/main/resources/db/migration/V15__ai_signal_evaluation.sql

CREATE TABLE ai_signal_evaluation (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    audit_id              UUID NOT NULL
                          REFERENCES ai_signal_audit(id) ON DELETE CASCADE,
    window_days           SMALLINT NOT NULL CHECK (window_days IN (7, 30)),

    -- 시점·가격
    signal_generated_at   TIMESTAMPTZ NOT NULL,
    evaluation_target_at  TIMESTAMPTZ NOT NULL,  -- signal_generated_at + window_days
    price_at_signal       NUMERIC(12,4) NOT NULL,
    price_at_window_end   NUMERIC(12,4) NOT NULL,
    end_trade_date        DATE NOT NULL,         -- 실제 종가로 사용한 거래일

    -- 판정
    signal                VARCHAR(16) NOT NULL,  -- audit 와 동일 enum 값 중복 저장 (조인 없이 집계)
    predicted_direction   VARCHAR(8)  NOT NULL,  -- UP / FLAT / DOWN
    actual_direction      VARCHAR(8)  NOT NULL,
    change_pct            NUMERIC(8,4) NOT NULL,
    hit                   BOOLEAN     NOT NULL,

    evaluated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    UNIQUE (audit_id, window_days)
);

CREATE INDEX idx_evaluation_window_evaluated
    ON ai_signal_evaluation (window_days, evaluated_at DESC);

CREATE INDEX idx_evaluation_window_signal
    ON ai_signal_evaluation (window_days, signal);

COMMENT ON TABLE  ai_signal_evaluation           IS 'AI 시그널 N일 후 방향 정합도 평가 결과 (파생 데이터).';
COMMENT ON COLUMN ai_signal_evaluation.hit       IS 'predicted_direction == actual_direction 여부.';
COMMENT ON COLUMN ai_signal_evaluation.change_pct IS '(end - signal) / signal * 100.';
```

---

## 4. API Specification

### 4.1 Endpoint List

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | `/api/v1/ai/accuracy` | 전역 정합도 집계 (window=7\|30) | Public |
| POST | `/api/admin/ai/backfill-evaluation` | 백필 트리거 (window=7\|30, since=ISO8601) | Basic Auth (admin) |

> 종목별(`?ticker=X`) 엔드포인트는 **Scope Out** — 개별 시그널 정합도 공개는 유사투자자문 해석 여지 있음.

### 4.2 Detailed Specification

#### `GET /api/v1/ai/accuracy?window=30`

**Request:** Query param `window` ∈ {7, 30}, default 30.

**Response (200 OK):**
```json
{
  "window": 30,
  "sampleSize": 142,
  "sampleSizeSufficient": true,
  "hitRate": 0.57,
  "bySignal": {
    "STRONG_BUY":  { "count": 8,  "hitRate": 0.75 },
    "BUY":         { "count": 41, "hitRate": 0.61 },
    "NEUTRAL":     { "count": 73, "hitRate": 0.53 },
    "SELL":        { "count": 18, "hitRate": 0.50 },
    "STRONG_SELL": { "count": 2,  "hitRate": 0.50 }
  },
  "evaluatedThrough": "2026-04-19T06:00:12Z",
  "disclaimer": "과거 성과는 미래 수익을 보장하지 않습니다. 본 정합도는 내부 튜닝 지표이며 투자 판단 근거가 아닙니다."
}
```

**Insufficient sample (sampleSize < 5):**
```json
{
  "window": 30,
  "sampleSize": 3,
  "sampleSizeSufficient": false,
  "hitRate": null,
  "bySignal": {},
  "evaluatedThrough": "2026-04-19T06:00:12Z",
  "message": "평가 누적 중",
  "disclaimer": "..."
}
```

**Error Responses:**
- `400 Bad Request`: `window` 값이 {7,30} 외 — `{ "error": { "code": "INVALID_WINDOW", "message": "window must be 7 or 30" } }`

#### `POST /api/admin/ai/backfill-evaluation`

**Request:**
```
Authorization: Basic <base64(admin:SECRET)>
Content-Type: application/json

{
  "window": 30,
  "since": "2026-03-01T00:00:00Z"  // optional, 기본: all
}
```

**Response (202 Accepted):**
```json
{
  "scheduled": true,
  "candidateCount": 184,
  "batchSize": 1000
}
```

실제 처리는 virtual-thread 로 비동기 (200ms 이내 응답).

---

## 5. UI/UX Design

### 5.1 Screen Layout (AI 분석 카드 확장)

```
┌───────────────────────────────────────────────────┐
│  🤖 AI 분석 (Gemini 2.5 Flash)                     │
│  ─────────────────────────────────────────────── │
│  Signal: BUY (confidence 0.72)                    │
│  Timeframe: 단기(1~2주)                           │
│                                                   │
│  [요약 2~4문장]                                   │
│                                                   │
│  근거:                                            │
│  • RSI 58 ...                                     │
│  • MACD 양전환 ...                                │
│                                                   │
│  리스크:                                          │
│  • ...                                            │
│                                                   │
│  ─────────────────────────────────────────────── │
│  📊 지난 30일 분석 방향 정합도  57%  (142건 기준) ⓘ │
│  ─────────────────────────────────────────────── │
│  ※ 본 분석은 투자 자문이 아닙니다 ...              │
└───────────────────────────────────────────────────┘
```

### 5.2 User Flow

```
StockDetailPage 로드
  → AI 분석 카드 렌더
  → useAccuracy({ window: 30 }) 병렬 호출
  → AccuracyBadge 마운트
     ├─ 로딩: skeleton
     ├─ sampleSizeSufficient=true: hitRate % + 샘플 수
     ├─ sampleSizeSufficient=false: "평가 누적 중" (회색)
     └─ error: 배지 자체 숨김 (silent fallback)

ⓘ 클릭
  → Tooltip / Popover 표시
  → bySignal 브레이크다운 표 + 면책 문구
```

### 5.3 Component List

| Component | Location | Responsibility |
|-----------|----------|----------------|
| `AiAccuracyBadge` | `src/features/stock-detail/components/ai-accuracy-badge.tsx` | 배지 렌더, 샘플 부족 시 숨김 |
| `AiAccuracyTooltip` | `src/features/stock-detail/components/ai-accuracy-tooltip.tsx` | bySignal 브레이크다운 팝오버 |
| `useAccuracy` | `src/features/stock-detail/hooks/use-accuracy.ts` | React Query, staleTime 1h |
| `fetchAiAccuracy` | `src/services/ai-accuracy.ts` | fetch wrapper |
| `AiAccuracyResponse` (type) | `src/types/ai-accuracy.ts` | DTO 타입 |

---

## 6. Error Handling

### 6.1 Error Code Definition

| Code | Message | Cause | Handling |
|------|---------|-------|----------|
| 400 | `INVALID_WINDOW` | window != 7,30 | FE: 기본 30 으로 재호출 |
| 401 | `UNAUTHORIZED` | admin 엔드포인트 인증 실패 | Basic Auth 재시도 |
| 500 | `EVALUATION_FAILED` | 캔들 DB 조회 실패 등 | 스케줄러: 로그 + Micrometer error count + 다음 실행 재시도. FE: 배지 숨김. |

### 6.2 Platform-Specific

- 캔들 DB 에 `signal_date + window_days` 종가 없을 시 → **다음 거래일 종가**로 fallback. 5일 이내 탐색 후 실패 시 `evaluation_failed`  카운트 증가, 해당 audit 은 건너뜀 (다음 배치 재시도).
- audit 의 `context_payload.quote.price` 가 null 인 과거 레코드 → `signal_date` 의 `candles.close` 로 fallback.

---

## 7. Security Considerations

- [x] 입력 검증: `window` param 화이트리스트 (`Set.of(7, 30)`)
- [x] 백필 엔드포인트 Basic Auth (Spring Security 기존 admin 체인)
- [x] 민감 정보 없음 (집계만 노출, 개별 audit 비공개)
- [x] HTTPS (Vercel·Fly.io TLS)
- [x] Rate Limiting: 공개 `/api/v1/ai/accuracy` 는 IP 당 60 req/min (기존 Bucket4j 체인 재사용)
- [x] SQL Injection 방지: JPA parameter binding only

---

## 8. Test Plan

### 8.1 Test Scope

| Type | Target | Tool |
|------|--------|------|
| Unit | `SignalOutcomeEvaluator` 방향 매핑·hit 판정 | JUnit 5 |
| Unit | Threshold boundary (±2% 경계) | JUnit 5, parameterized |
| Integration | 스케줄러 1회 실행 후 레코드 검증 | Spring Boot Test + Testcontainers |
| Integration | 집계 API 응답 구조·캐시 | MockMvc + Redis embedded |
| Integration | 백필 admin 엔드포인트 Basic Auth | Spring Security Test |
| FE Unit | `AiAccuracyBadge` 조건부 렌더 | Jest + RTL |
| Contract | OpenAPI 스키마 검증 | springdoc-openapi + ts 생성 |

### 8.2 Test Cases (Key)

- [ ] `SignalOutcomeEvaluator`: BUY + actual +3% → hit=true (UP vs UP)
- [ ] `SignalOutcomeEvaluator`: BUY + actual -0.5% → hit=false (UP vs FLAT)
- [ ] `SignalOutcomeEvaluator`: NEUTRAL + actual ±1% → hit=true (FLAT vs FLAT)
- [ ] `SignalOutcomeEvaluator`: NEUTRAL + actual +3% → hit=false
- [ ] `SignalOutcomeEvaluator`: STRONG_SELL + actual -5% → hit=true
- [ ] Edge: change_pct == ±2% (경계) → flat 처리 (inclusive)
- [ ] Scheduler: 동일 audit 재실행 → UNIQUE 제약으로 중복 없음 (on conflict do nothing)
- [ ] Scheduler: candle 누락 → 건너뜀, 에러 로그, 다음 배치에서 재시도
- [ ] API: sampleSize < 5 → `sampleSizeSufficient: false`, `hitRate: null`
- [ ] API: window=14 → 400 Bad Request
- [ ] FE: sampleSize=0 → 배지 전체 숨김
- [ ] FE: fetch error → 배지 숨김 (에러 UI 노출 안 함)

---

## 9. Clean Architecture

### 9.1 Layer Structure

| Layer | Responsibility | Location |
|-------|---------------|----------|
| Presentation (FE) | UI 컴포넌트, 훅 | `apps/web/src/features/stock-detail/components/`, `hooks/` |
| Application (FE) | React Query 훅, 서비스 호출 | `apps/web/src/services/`, `features/*/hooks/` |
| Domain (FE) | 타입 | `apps/web/src/types/ai-accuracy.ts` |
| Infrastructure (FE) | API fetch | `apps/web/src/services/ai-accuracy.ts` |
| Web (BE) | 컨트롤러 | `apps/api/.../ai/web/` |
| Service (BE) | 오케스트레이션·집계 | `apps/api/.../ai/service/` |
| Domain (BE) | Pure evaluator | `apps/api/.../ai/domain/SignalOutcome.java`, `SignalOutcomeEvaluator.java` |
| Infra (BE) | JPA Entity/Repository | `apps/api/.../ai/infra/` |

### 9.2 Dependency Rules

- BE: `web → service → domain`, `service → infra → domain`. Domain 은 의존 0.
- FE: `components → hooks → services → types`. Components 가 services 직접 호출 금지.

---

## 10. Coding Convention Reference

### 10.1 Naming Conventions (프로젝트 기준)

| Target | Rule | Example |
|--------|------|---------|
| FE Components | PascalCase export, kebab-case 파일 | `AiAccuracyBadge` → `ai-accuracy-badge.tsx` |
| FE Hooks | camelCase export, kebab-case 파일 | `useAccuracy` → `use-accuracy.ts` |
| BE Classes | PascalCase | `SignalOutcomeEvaluator` |
| Flyway | `V{n}__{snake_case}.sql` | `V15__ai_signal_evaluation.sql` |

### 10.2 Forbidden Terms (신규 추가)

`.github/workflows/forbidden-terms.yml` 에 **CI 전용 2차 패스**로 추가:
- 정확도, 예측, 적중, 적중률, 승률

**결정: `forbidden-terms.json` 에는 포함하지 않음.** 이 JSON 은 런타임 `LegalGuardFilter`
가 모든 `/api/v1/**` 응답을 스캔할 때 사용되는데, "예측" 같은 단어는 뉴스 번역이나
분석가 코멘트에서 중립적 맥락으로 자주 등장해 오탐 위험이 크다. 따라서 AI 출력 런타임
차단은 "투자 자문 유도" 계열(v1.1 기준 54종)로 유지하고, 이 5종은 **소스 코드 정적
스캔 전용**으로만 운영한다 (CI pass 2).

`disclaimer-footer` 수준의 기본 면책은 유지. 단 **`legal/` 디렉토리 예외**는 이미 적용됨.

### 10.3 Environment Variables (신규)

| Variable | Purpose | Scope | Default |
|----------|---------|-------|---------|
| `APP_AI_EVALUATION_ENABLED` | 스케줄러 on/off | Server | `true` |
| `APP_AI_EVALUATION_WINDOWS` | 활성 window (csv) | Server | `7,30` |
| `APP_AI_EVALUATION_FLAT_THRESHOLD_PCT` | Neutral 판정 경계 | Server | `2.0` |
| `APP_AI_EVALUATION_CRON` | 스케줄 cron | Server | `0 0 6 * * *` (UTC) |
| `APP_ADMIN_USER` / `APP_ADMIN_PASSWORD` | 백필 엔드포인트 Basic Auth | Server | (기존 admin 체인 재사용) |

---

## 11. Implementation Guide

### 11.1 File Structure (신규/수정)

**Backend (apps/api):**
```
src/main/java/com/aistockadvisor/ai/
├── domain/
│   ├── SignalOutcome.java              [NEW]
│   ├── SignalOutcomeEvaluator.java     [NEW]  (pure)
│   └── EvaluationWindow.java           [NEW]
├── infra/
│   ├── AiSignalEvaluationEntity.java   [NEW]
│   └── AiSignalEvaluationRepository.java [NEW]
├── service/
│   ├── SignalEvaluationService.java    [NEW]
│   ├── SignalEvaluationScheduler.java  [NEW]
│   └── SignalAccuracyService.java      [NEW]
└── web/
    ├── SignalAccuracyController.java   [NEW]
    ├── AdminEvaluationController.java  [NEW]
    └── dto/
        ├── AccuracyResponse.java       [NEW]
        └── BackfillRequest.java        [NEW]

src/main/resources/db/migration/
└── V15__ai_signal_evaluation.sql       [NEW]

src/main/resources/application.yml      [MODIFY: app.ai.evaluation.*]

src/test/java/com/aistockadvisor/ai/
├── domain/
│   └── SignalOutcomeEvaluatorTest.java [NEW]
├── service/
│   └── SignalEvaluationServiceIT.java  [NEW]
└── web/
    └── SignalAccuracyControllerIT.java [NEW]
```

**Frontend (apps/web):**
```
src/
├── features/stock-detail/
│   ├── components/
│   │   ├── ai-accuracy-badge.tsx       [NEW]
│   │   └── ai-accuracy-tooltip.tsx     [NEW]
│   ├── hooks/
│   │   └── use-accuracy.ts             [NEW]
│   └── components/ai-analysis-card.tsx [MODIFY: 배지 삽입]
├── services/
│   └── ai-accuracy.ts                  [NEW]
└── types/
    └── ai-accuracy.ts                  [NEW]
```

**CI:**
```
.github/workflows/forbidden-terms.yml   [MODIFY: 신규 용어 추가]
```

### 11.2 Implementation Order (Do Phase Steps)

1. **Step 1 — DB 스키마**
   - `V15__ai_signal_evaluation.sql` 작성
   - `AiSignalEvaluationEntity`, `AiSignalEvaluationRepository`
   - `AiSignalAuditRepository` 확장: `findUnevaluatedBefore(Instant threshold, int windowDays, Pageable)` 쿼리 추가
   - 로컬 Testcontainers 마이그레이션 통과 확인

2. **Step 2 — Pure domain evaluator**
   - `SignalOutcome`, `EvaluationWindow`
   - `SignalOutcomeEvaluator` (Signal→Direction 매핑, actual 판정, hit 계산)
   - `SignalOutcomeEvaluatorTest` (경계·Neutral·Strong 케이스 전부)

3. **Step 3 — Evaluation service**
   - `SignalEvaluationService.evaluateWindow(int windowDays, Instant since)`:
     - audit 페이지네이션 조회
     - price_at_signal (context_payload.quote.price 파싱, fallback: candle[signal_date])
     - price_at_window_end (candle[signal_date + windowDays, next business day])
     - evaluator 호출 → entity 생성 → save (on conflict skip)
   - 단위 테스트 mocked, 통합 테스트 Testcontainers

4. **Step 4 — Scheduler + Admin endpoint**
   - `SignalEvaluationScheduler` (`@Scheduled(cron = "${app.ai.evaluation.cron}")`)
     - windows 순회하며 `evaluateWindow(w, null)` 호출
     - profile `test` 제외
   - `AdminEvaluationController.POST /api/admin/ai/backfill-evaluation`
   - Basic Auth 기존 admin 체인 적용 확인

5. **Step 5 — Accuracy API**
   - `SignalAccuracyService.summarize(int window)`:
     - Redis 1h 캐시 (`ai:accuracy:w30`)
     - JPQL 집계 쿼리 (count, sum(hit))
     - bySignal 그룹핑
   - `SignalAccuracyController.GET /api/v1/ai/accuracy`
   - OpenAPI 문서화 (기존 springdoc 설정)

6. **Step 6 — FE 타입·서비스·훅**
   - `types/ai-accuracy.ts`
   - `services/ai-accuracy.ts`
   - `hooks/use-accuracy.ts` (React Query, `staleTime: 1h`, retry: 1)

7. **Step 7 — FE 컴포넌트**
   - `AiAccuracyBadge` 마운트 `AiAnalysisCard` 하단
   - `AiAccuracyTooltip` bySignal 테이블
   - Jest + RTL 조건부 렌더 테스트

8. **Step 8 — Forbidden terms CI 강화**
   - 신규 용어 5개 추가
   - 로컬 grep 검증 후 PR

9. **Step 9 — 백필 1회 실행**
   - dev/staging 에서 admin 엔드포인트 호출
   - 수동 검산 3건 (AAPL / TSLA / NVDA)
   - 결과 기록 후 Gap analysis 진입

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 0.1 | 2026-04-20 | Initial draft (A: 신뢰도 입증 측정 인프라) | wonseok-han |
