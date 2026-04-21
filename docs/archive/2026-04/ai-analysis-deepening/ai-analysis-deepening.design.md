# ai-analysis-deepening Design Document

> **Summary**: 로그인 유저 전용 AI 참고 분석 고도화. 프롬프트 스키마 확장(4 필드) + 뉴스 freshness + FE 섹션 리팩터.
>
> **Project**: AI Stock Advisor
> **Version**: v0.1.0 Beta
> **Author**: wonseok-han
> **Date**: 2026-04-21
> **Status**: Draft
> **Planning Doc**: [ai-analysis-deepening.plan.md](../../01-plan/features/ai-analysis-deepening.plan.md)

---

## 1. Overview

### 1.1 Design Goals

- **설명력 강화**: 시그널/신뢰도 이상의 "왜"·"무슨 뜻"·"앞으로 뭘 볼지" 를 한 응답에 실음
- **뉴스 신선도 가시화**: 뉴스별 `hours_ago` + `FRESH/RECENT/STALE` 배지
- **무해한 확장**: 기존 `rationale`/`risks` 필드 유지 → FE/DB 양쪽 호환
- **법적 원칙 유지**: 신규 필드 전부 LegalGuardFilter + CI forbidden-terms 동일 가드

### 1.2 Design Principles

- **필드 추가만, 삭제/변경 없음** — 전환기 리스크 최소화
- **옵셔널 + graceful degrade** — 신규 필드 비었어도 FE 기존 동작
- **단일 JSONB 확장 컬럼** — DB 스키마 flux 구간 단순화
- **Korean only, 설명형 서술만** — 명령형(사세요/파세요) 금지

---

## 2. Architecture

### 2.1 Component Diagram

```
┌─────────────────┐     ┌──────────────────────────────────┐     ┌─────────────┐
│ Next.js (web)   │────▶│ Spring Boot (api)                │────▶│ PostgreSQL  │
│ AiSignalPanel   │     │  AiSignalController              │     │ ai_signal_   │
│  ├ SignalHero   │     │   └ AiSignalService              │     │  audit      │
│  ├ BeginnerBox  │     │       ├ ContextAssembler(+news5) │     │  (+ext col) │
│  ├ IndicatorIntp│     │       ├ PromptBuilder(+v2 tmpl)  │     └─────────────┘
│  ├ NewsImpact   │     │       ├ LlmClient(Gemini 2.5F)   │
│  ├ WhatToWatch  │     │       ├ ResponseValidator(+opt)  │
│  └ RisksList    │     │       └ Redis cache v2           │
└─────────────────┘     └──────────────────────────────────┘
```

### 2.2 Data Flow

```
[FE] 종목 상세 진입 (로그인)
  → GET /api/v1/ai/signal?ticker=...&tf=...
  → [BE] 캐시 v2 확인 → miss 시:
      ContextAssembler (profile+quote+indicators+news[5] with freshness)
      → PromptBuilder (system v2 + user JSON)
      → Gemini 2.5 Flash
      → ResponseValidator (기본 6 + 확장 4 옵셔널 검증, 금지어 스캔)
      → ai_signal_audit INSERT (extended_response JSONB 포함)
      → AiSignal DTO
  → [FE] 섹션 렌더
      핵심 신호(항상) + 초보자 해설 + 지표 해석 + 뉴스 영향 + 관찰 포인트 + 리스크
```

### 2.3 Dependencies

| Component | Depends On | Purpose |
|-----------|-----------|---------|
| PromptBuilder | prompts/ai-signal.system.txt (v2) | 시스템 프롬프트 |
| ContextAssembler | NewsService (limit=5) | 뉴스 확대 + freshness 계산 |
| ResponseValidator | ForbiddenTermsRegistry | 금지어 재검증 (신규 필드 포함) |
| AiSignalAuditEntity | V16 migration | extended_response JSONB 컬럼 |
| FE panel | useAiSignal 훅 (기존) | 응답 옵셔널 필드 파싱 |

---

## 3. Data Model

### 3.1 Extended AI Signal (Domain)

```java
// Java domain — apps/api/src/main/java/com/aistockadvisor/ai/domain/
public record AiSignal(
    String ticker,
    Signal signal,            // 기존
    double confidence,        // 기존
    Timeframe timeframe,      // 기존
    List<String> rationale,   // 기존
    List<String> risks,       // 기존
    String summaryKo,         // 기존
    // ↓ v2 확장 (모두 nullable/Optional — 구 응답과 공존)
    String beginnerExplanation,
    List<IndicatorInterpretation> indicatorInterpretation,
    List<NewsImpact> newsImpact,
    List<String> whatToWatch,
    Instant generatedAt,
    String modelName,
    String disclaimer,
    boolean fallback
) { }

public record IndicatorInterpretation(
    String indicator,   // "MACD" | "BOLLINGER" | "RSI" | "MA"
    String value,       // 표시 값 (예: "67.3", "MACD 0.8 / Signal 0.5")
    String meaningKo    // 초보자 설명 (1-2 문장)
) { }

public record NewsImpact(
    String titleKo,         // 뉴스 제목 (번역본)
    ImpactDirection impact, // POSITIVE | NEGATIVE | NEUTRAL
    String reasonKo,        // 왜 그렇게 영향할 수 있는지 (1-2 문장)
    Integer hoursAgo        // 발행 경과 시간 (freshness 배지용)
) { }

public enum ImpactDirection { POSITIVE, NEGATIVE, NEUTRAL }
```

```typescript
// TypeScript — apps/web/src/types/ai-signal.ts
export interface AiSignal {
  // 기존 유지
  ticker: string;
  signal: AiSignalClass;
  confidence: number;
  timeframe: AiSignalTimeframe;
  rationale: string[];
  risks: string[];
  summaryKo: string;
  generatedAt: string;
  modelName: string;
  disclaimer: string;
  fallback: boolean;
  // v2 확장 (optional)
  beginnerExplanation?: string;
  indicatorInterpretation?: IndicatorInterpretation[];
  newsImpact?: NewsImpact[];
  whatToWatch?: string[];
}

export interface IndicatorInterpretation {
  indicator: 'MACD' | 'BOLLINGER' | 'RSI' | 'MA';
  value: string;
  meaningKo: string;
}

export type NewsImpactDirection = 'POSITIVE' | 'NEGATIVE' | 'NEUTRAL';

export interface NewsImpact {
  titleKo: string;
  impact: NewsImpactDirection;
  reasonKo: string;
  hoursAgo: number | null;
}
```

### 3.2 Database Schema — V16 Migration

```sql
-- apps/api/src/main/resources/db/migration/V16__ai_audit_extended.sql

ALTER TABLE ai_signal_audit
  ADD COLUMN extended_response JSONB;

COMMENT ON COLUMN ai_signal_audit.extended_response IS
  'v2 확장 필드 번들 (beginner_explanation / indicator_interpretation / news_impact / what_to_watch). 구 응답은 NULL.';

-- 통계·분석용 부분 인덱스 (응답 유무 구분)
CREATE INDEX idx_ai_signal_audit_extended_presence
  ON ai_signal_audit ((extended_response IS NOT NULL));
```

**저장 구조** (JSONB 내부):
```json
{
  "beginner_explanation": "...",
  "indicator_interpretation": [ {"indicator": "RSI", "value": "67.3", "meaning_ko": "..."} ],
  "news_impact": [ {"title_ko": "...", "impact": "POSITIVE", "reason_ko": "...", "hours_ago": 3} ],
  "what_to_watch": ["...", "..."],
  "schema_version": "v2"
}
```

### 3.3 ContextAssembler Extension — News Freshness

```java
// recent_news 각 항목에 freshness 메타 삽입
{
  "title": "...",
  "summary": "...",
  "sentiment": "POSITIVE",
  "published_at": "2026-04-21T02:00:00Z",
  "hours_ago": 3,             // ← 신규
  "freshness": "FRESH"        // ← 신규 — FRESH(<24h) / RECENT(<72h) / STALE
}
```

- 건수: 3 → **5** (NewsService DEFAULT_LIMIT 5 재사용, getNews(ticker, 5) 호출)
- 계산: `hoursAgo = Duration.between(publishedAt, now).toHours()`
- 티어 임계치: 24h / 72h / 그 이후

---

## 4. API Specification

### 4.1 Endpoint (변경 없음, 응답만 확장)

| Method | Path | Auth | 변경 |
|--------|------|------|-----|
| GET | `/api/v1/ai/signal?ticker=&tf=` | JWT Required | 응답 JSON 에 4 필드 추가 (옵셔널) |

### 4.2 Extended Response (200 OK)

```json
{
  "ticker": "AAPL",
  "signal": "BUY",
  "confidence": 0.68,
  "timeframe": "MID",
  "rationale": ["...", "..."],
  "risks": ["...", "..."],
  "summaryKo": "...",
  "beginnerExplanation": "최근 주가는 상승 추세에 있습니다. RSI 가 과매수 근접(67)이라는 건 '단기 과열' 가능성이 있다는 뜻입니다. ...",
  "indicatorInterpretation": [
    { "indicator": "RSI", "value": "67.3", "meaningKo": "70 이상이면 과매수, 30 이하면 과매도로 해석합니다. 67 은 과매수 경계 근접." },
    { "indicator": "MACD", "value": "0.82 / 0.55", "meaningKo": "MACD 가 시그널선 위에 있고 격차가 벌어지는 중 — 단기 상승 모멘텀 시사." },
    { "indicator": "BOLLINGER", "value": "상단 근접 (%B 0.88)", "meaningKo": "밴드 상단에 닿는다는 건 단기 변동성 확대 가능성을 의미합니다." },
    { "indicator": "MA", "value": "MA5 > MA20 > MA60", "meaningKo": "정배열 — 단기·중기·장기 모두 상승 방향 정렬." }
  ],
  "newsImpact": [
    { "titleKo": "애플, 중국 매출 부진 전망", "impact": "NEGATIVE", "reasonKo": "주요 시장 매출 감소는 분기 실적 우려로 연결될 수 있습니다.", "hoursAgo": 2 },
    { "titleKo": "iPhone 18 Pro 공개 일정 관련 보도", "impact": "POSITIVE", "reasonKo": "신제품 기대감은 단기 주가 지지 요인으로 작용할 수 있습니다.", "hoursAgo": 18 }
  ],
  "whatToWatch": [
    "다음 실적 발표(2026-05-02) 전까지 중국 매출 지표 보도",
    "RSI 70 돌파 여부 — 돌파 시 단기 과열 조정 가능성",
    "뉴스 sentiment 변화 — 하루 단위로 FRESH 뉴스 sentiment 재확인"
  ],
  "generatedAt": "2026-04-21T03:12:00Z",
  "modelName": "gemini-2.5-flash",
  "disclaimer": "본 정보는 투자 자문이 아니며 ...",
  "fallback": false
}
```

### 4.3 Caching

- **캐시 키**: `ai:{ticker}:{tf}:v2` (v1 → v2 bump — 확장 필드 포함)
- TTL: 60 분 (기존 유지)
- 전환: v1 캐시는 자연 만료로 소멸, 별도 invalidation 없음

---

## 5. UI/UX Design

### 5.1 Screen Layout (Desktop)

```
┌────────────────────────────────────────────────────┐
│ AI 참고 분석                       [중기 관점]      │
├────────────────────────────────────────────────────┤
│ ┌──────────────────────────────────────────────┐   │
│ │ [긍정]              신뢰도 68%               │   │
│ │ ████████████████████▱▱▱▱▱▱▱                 │   │
│ └──────────────────────────────────────────────┘   │
│                                                    │
│ 📖 초보자를 위한 설명                              │
│ ┌──────────────────────────────────────────────┐   │
│ │ 최근 주가는 상승 추세에 있습니다. RSI ...    │   │
│ └──────────────────────────────────────────────┘   │
│                                                    │
│ 📊 지표 해석                             [접기 ▼]  │
│ ┌──────────────────────────────────────────────┐   │
│ │ RSI 67.3 — 70 이상이면 과매수 ...            │   │
│ │ MACD 0.82/0.55 — 시그널선 위 ...             │   │
│ │ BOLLINGER 상단 근접 — ...                    │   │
│ │ MA 정배열 — ...                              │   │
│ └──────────────────────────────────────────────┘   │
│                                                    │
│ 📰 뉴스 영향 (5건 중 4건 FRESH)          [접기 ▼]  │
│ ┌──────────────────────────────────────────────┐   │
│ │ [🟢 POSITIVE] [2시간 전] [FRESH]             │   │
│ │ iPhone 18 Pro 공개 일정 ...                  │   │
│ │ └ 신제품 기대감은 ...                        │   │
│ │ [🔴 NEGATIVE] [18시간 전] [FRESH]            │   │
│ │ 애플, 중국 매출 부진 ...                     │   │
│ │ └ 주요 시장 매출 감소는 ...                  │   │
│ └──────────────────────────────────────────────┘   │
│                                                    │
│ 👀 관찰 포인트                                     │
│ • 다음 실적 발표(2026-05-02) 전까지 ...           │
│ • RSI 70 돌파 여부                                 │
│                                                    │
│ ⚠️ 리스크                                          │
│ • ...                                              │
│                                                    │
│ [면책] + [30일 방향 일치율 배지]                   │
└────────────────────────────────────────────────────┘
```

### 5.2 Mobile Layout

- 기본 펼침: 핵심 신호 / 초보자 해설 / 뉴스 영향 (상위 2건) / 리스크 (상위 2건)
- 기본 접힘: 지표 해석 / 관찰 포인트 / 뉴스 전체
- 접이식 토글은 섹션 헤더 클릭·키보드 Enter·스페이스
- 상태 `aria-expanded` 로 명시

### 5.3 Component List

| Component | Location | Responsibility |
|-----------|----------|----------------|
| `AiSignalPanel` | `features/stock-detail/ai-signal/ai-signal-panel.tsx` | 기존 — 섹션 조합만 담당하도록 축소 |
| `SignalHero` | 기존 (panel 내부) | 5-class 배지 + 신뢰도 바 |
| `BeginnerExplanation` | `ai-signal/components/beginner-explanation.tsx` | 신규 — 2-4 문장 해설 블록 |
| `IndicatorInterpretation` | `ai-signal/components/indicator-interpretation.tsx` | 신규 — 지표 리스트 (접이식) |
| `NewsImpact` | `ai-signal/components/news-impact.tsx` | 신규 — 뉴스 영향도 리스트 (접이식) |
| `WhatToWatch` | `ai-signal/components/what-to-watch.tsx` | 신규 — 관찰 포인트 리스트 |
| `CollapsibleSection` | `ai-signal/components/collapsible-section.tsx` | 신규 — 공통 토글 컨테이너 (aria-expanded) |
| `FreshnessBadge` | `ai-signal/components/freshness-badge.tsx` | 신규 — FRESH/RECENT/STALE + hoursAgo |
| `useAiSignal` | 기존 훅 | 응답 타입 확장만 |

### 5.4 Graceful Degrade

| 상황 | FE 동작 |
|---|---|
| `beginnerExplanation == null` | 해설 섹션 숨김 |
| `indicatorInterpretation` 빈 배열 | 지표 섹션 숨김 |
| `newsImpact` 빈 배열 | 뉴스 섹션 숨김 |
| `whatToWatch` 빈 배열 | 관찰 포인트 섹션 숨김 |
| `fallback == true` | 기존 NEUTRAL 배너 + 확장 섹션 전부 숨김 |

---

## 6. Error Handling

### 6.1 LLM 응답 케이스

| 상황 | 처리 |
|------|------|
| 확장 필드 누락 (`indicator_interpretation` 없음) | 검증 통과 — nullable/empty 허용. FE 에서 섹션 숨김 |
| 확장 필드 타입 오류 (e.g., `impact` 비표준) | 검증 실패 — v1 fallback 객체로 저장 (확장 필드 null) |
| 기본 6 필드 누락 | 기존과 동일 — `fallback=true` 응답 |
| 금지어 hit (신규 필드 포함) | LegalGuardFilter 가 전체 응답 neutralize (기존 Level 4) |
| 프롬프트 토큰 초과 | Gemini 측 에러 → 기존 fallback |

### 6.2 검증 로직 확장 (ResponseValidator)

```java
// 기존 RawSignal record 확장
@JsonIgnoreProperties(ignoreUnknown = true)
private record RawSignal(
    String signal, Double confidence, String timeframe,
    List<String> rationale, List<String> risks, String summary_ko,
    // v2 — 전부 nullable
    String beginner_explanation,
    List<RawIndicator> indicator_interpretation,
    List<RawNewsImpact> news_impact,
    List<String> what_to_watch
) {}

// 확장 필드는 "존재하면 타입만 검증, 없으면 통과"
// 금지어 스캔은 모든 문자열 합쳐서 기존과 동일 방식
```

---

## 7. Security Considerations

- [x] **LegalGuardFilter** 기존 체인 재사용 — 모든 `/api/v1/**` 응답 텍스트 스캔 대상
- [x] **ResponseValidator** 금지어 스캔에 확장 필드 텍스트 포함
- [x] **CI forbidden-terms** — 신규 FE 컴포넌트 4개 / BE 도메인 클래스 / V16 SQL 전부 스캔 대상
- [x] **JWT auth** — 기존 `/api/v1/ai/signal` 체인 유지 (로그인 유저만)
- [x] **Rate limit** — 기존 AiSignalRateLimiter 그대로
- [ ] **토큰 증가 모니터링** — tokensIn/tokensOut 로그 관찰 (before/after 스냅샷 Do 단계에서 기록)
- [x] **초보자 해설 수위** — system prompt 에 "설명형 서술만, 명령형·확정적 예언 금지" 명시

---

## 8. Test Plan

### 8.1 Test Scope

| Type | Target | Tool |
|------|--------|------|
| Unit (BE) | ResponseValidator 확장 — 신규 필드 타입 검증 | JUnit 5 |
| Unit (BE) | ContextAssembler 뉴스 freshness 계산 (경계값: 23h/24h/71h/72h) | JUnit 5 |
| Integration (BE) | AiSignalService E2E — Gemini mock 으로 확장 응답 반환 → DB `extended_response` 저장 확인 | Testcontainers + Mockito |
| Contract (BE) | RawSignal 역직렬화 — legacy 응답 / v2 응답 / 부분 응답 | JUnit 5 |
| Prompt | AAPL/TSLA/NVDA 수동 샘플링 3회 × 3종목 → 응답 구조 검수 (Do 단계 기록) | 수동 |
| FE (manual) | 섹션 렌더 · 접이식 · 키보드 포커스 · graceful degrade | 브라우저 검증 (Jest 부재) |

### 8.2 Test Cases (Key)

- [ ] Happy path: 4 신규 필드 전부 채워진 응답 — DTO/DB/FE 모두 정상
- [ ] 부분 응답: `indicator_interpretation` 만 비어있음 — 나머지 섹션 렌더, 지표 섹션 숨김
- [ ] 레거시 응답: v2 필드 전혀 없음 — 기존 UI 와 동일 (rationale/risks 표시)
- [ ] 타입 오류: `impact: "BULL"` (허용 enum 아님) → 검증 실패 → fallback 응답
- [ ] 금지어: `what_to_watch` 에 "매수하세요" 포함 → forbidden 감지 → fallback
- [ ] 뉴스 freshness: 23h → FRESH / 25h → RECENT / 73h → STALE
- [ ] 뉴스 0건: `newsImpact: []` → 뉴스 섹션 숨김
- [ ] fallback=true: 모든 확장 섹션 숨김, 기존 NEUTRAL 배너
- [ ] 캐시 v2: 첫 호출 miss → LLM → set, 두 번째 hit → LLM 미호출
- [ ] DB migration: 기존 row `extended_response IS NULL` — 쿼리/조회 정상

---

## 9. Clean Architecture

### 9.1 Layer Structure (Dynamic Level)

| Layer | Responsibility | Location |
|-------|---------------|----------|
| **Presentation (FE)** | 섹션 컴포넌트 + 접이식 토글 | `apps/web/src/features/stock-detail/ai-signal/components/` |
| **Application (FE)** | useAiSignal 훅 (기존) | `apps/web/src/features/stock-detail/ai-signal/hooks/` |
| **Domain** | AiSignal / IndicatorInterpretation / NewsImpact types | `apps/web/src/types/ai-signal.ts` · `apps/api/.../ai/domain/` |
| **Application (BE)** | AiSignalService / ContextAssembler / PromptBuilder / ResponseValidator | `apps/api/.../ai/service/` |
| **Infrastructure (BE)** | LlmClient / AiSignalAuditEntity / AiSignalAuditRepository | `apps/api/.../ai/infra/` |

### 9.4 This Feature's Layer Assignment

| Component | Layer | Location |
|-----------|-------|----------|
| `BeginnerExplanation` / `IndicatorInterpretation` / `NewsImpact` / `WhatToWatch` | Presentation | `features/stock-detail/ai-signal/components/` |
| `CollapsibleSection` | Presentation (공통) | `features/stock-detail/ai-signal/components/` |
| `useAiSignal` | Application | 기존 위치 유지 |
| `AiSignal` / `IndicatorInterpretation` / `NewsImpact` (types) | Domain | `types/ai-signal.ts` |
| `AiSignalService` / `ContextAssembler` / `PromptBuilder` / `ResponseValidator` | Application (BE) | `ai/service/` |
| `AiSignalAuditEntity` (V16 컬럼) | Infrastructure | `ai/infra/` |

---

## 10. Coding Convention Reference

### 10.1 Naming (이 프로젝트 컨벤션)

| Target | Rule | Example |
|--------|------|---------|
| FE Components | PascalCase (export) / **kebab-case (file)** | `BeginnerExplanation` → `beginner-explanation.tsx` |
| BE Classes | PascalCase | `IndicatorInterpretation.java` |
| BE Methods | camelCase | `computeFreshness()` |
| JSON keys | snake_case (LLM 응답) / camelCase (FE 응답) | `indicator_interpretation` ↔ `indicatorInterpretation` |
| DB columns | snake_case | `extended_response` |

### 10.2 Forbidden Terms 가드 확장

FE 신규 컴포넌트는 정합도 도메인 5종(§10.3) 금지어 스캔 대상. 주석 작성 시 직접 인용 금지 — `§10.2` 링크로 대체.

### 10.3 이 Feature 의 컨벤션

| Item | 적용 |
|------|-----|
| FE 파일명 | kebab-case (`what-to-watch.tsx`) |
| FE export | PascalCase (`WhatToWatch`) |
| 상태 관리 | 섹션 접이식 상태는 컴포넌트 내 `useState` — 패널 수준 전역 상태 불필요 |
| 에러 처리 | AiSignal 응답 null/empty → 섹션 숨김 (graceful) |
| i18n | 모든 신규 문자열 Korean only (system prompt rule 1) |

---

## 11. Implementation Guide

### 11.1 File Structure

```
apps/api/
├── src/main/java/com/aistockadvisor/ai/
│   ├── domain/
│   │   ├── AiSignal.java                         # 확장 (4 필드 + nullable)
│   │   ├── IndicatorInterpretation.java          # 신규 record
│   │   ├── NewsImpact.java                       # 신규 record
│   │   └── ImpactDirection.java                  # 신규 enum
│   ├── service/
│   │   ├── ContextAssembler.java                 # 뉴스 5 + freshness
│   │   ├── PromptBuilder.java                    # user prompt 수정 (news_freshness 포함)
│   │   ├── ResponseValidator.java                # RawSignal 확장 + 옵셔널 파싱
│   │   └── AiSignalService.java                  # DTO 매핑 + audit 저장 확장
│   └── infra/
│       └── AiSignalAuditEntity.java              # extendedResponse 필드 추가
├── src/main/resources/
│   ├── prompts/ai-signal.system.txt              # v2 스키마 반영
│   └── db/migration/V16__ai_audit_extended.sql   # 신규

apps/web/
└── src/features/stock-detail/ai-signal/
    ├── ai-signal-panel.tsx                       # 섹션 조합만 담당 (리팩터)
    ├── components/
    │   ├── beginner-explanation.tsx              # 신규
    │   ├── indicator-interpretation.tsx          # 신규
    │   ├── news-impact.tsx                       # 신규
    │   ├── what-to-watch.tsx                     # 신규
    │   ├── collapsible-section.tsx               # 신규 공통
    │   └── freshness-badge.tsx                   # 신규
    └── hooks/use-ai-signal.ts                    # 타입만 확장 (동작 변경 없음)

apps/web/src/types/ai-signal.ts                   # 확장
```

### 11.2 Implementation Order

1. [ ] **Step 1 — BE Domain 확장**: AiSignal record 확장 + IndicatorInterpretation / NewsImpact / ImpactDirection 신규 타입
2. [ ] **Step 2 — V16 Migration**: `extended_response` JSONB 컬럼 + 부분 인덱스 + AiSignalAuditEntity JSONB 매핑
3. [ ] **Step 3 — ContextAssembler 확장**: news 5건 + freshness(hoursAgo/freshness) 계산 + 경계값 유닛 테스트
4. [ ] **Step 4 — PromptBuilder / system prompt v2**: 출력 JSON 스키마 교체, 예시 삽입, 금지 규칙 유지
5. [ ] **Step 5 — ResponseValidator 확장**: 옵셔널 필드 파싱 + 금지어 스캔 범위 확대 + 단위 테스트
6. [ ] **Step 6 — AiSignalService 매핑**: DTO 확장 반영 + audit 저장에 `extendedResponse` 포함 + 캐시 키 v2
7. [ ] **Step 7 — FE 타입·훅 확장**: `AiSignal` 타입 + useAiSignal 타입만 확장
8. [ ] **Step 8 — FE 신규 컴포넌트 6개**: CollapsibleSection / BeginnerExplanation / IndicatorInterpretation / NewsImpact (FreshnessBadge 포함) / WhatToWatch
9. [ ] **Step 9 — FE 패널 리팩터**: ai-signal-panel.tsx 를 섹션 조합 중심으로 재구성 + graceful degrade
10. [ ] **Step 10 — 프롬프트 dry-run 샘플링**: AAPL/TSLA/NVDA 3회씩 수동 실행 → 응답 구조·토큰 사용량 기록 → design 부록 업데이트
11. [ ] **Step 11 — CI / 빌드 검증**: forbidden-terms / ESLint / tsc / Gradle check 전체 그린 확인

각 Step 완료 시 커밋 (feat(ai-analysis-deepening): Step N — ...). Step 10 은 운영 환경에서만 의미 — 로컬 Gemini 키로 3종목만 검증.

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 0.1 | 2026-04-21 | Initial draft | wonseok-han |
