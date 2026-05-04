# timing-signal Design Document

> **Summary**: 기존 AI 시그널 응답에 "지금이니?!" 타이밍 판정(진입 조건 충족도) 섹션을 추가. Gemini 프롬프트 확장 + 도메인 모델 추가 + FE 타이밍 히어로 카드.
>
> **Project**: nowini
> **Version**: v0.1.0
> **Author**: wonseok-han
> **Date**: 2026-05-04
> **Status**: Draft
> **Planning Doc**: [timing-signal.plan.md](../../01-plan/features/timing-signal.plan.md)

---

## 1. Overview

### 1.1 Design Goals

- 기존 `AiSignal` 응답 구조에 **하위 호환** 되게 `timing` 필드 추가 (기존 short_term/long_term 무영향)
- Gemini 프롬프트 스키마 확장으로 구현 — **추가 API 호출 0**, 기존 컨텍스트 100% 재활용
- 타이밍 판정이 없어도 (Gemini 누락, fallback 시) 기존 시그널 정상 동작 보장
- 면책 원칙 일관 준수 — "진입 조건 충족 여부" 프레이밍, 매수 권유 표현 금지

### 1.2 Design Principles

- **Additive-only** — 기존 JSON 스키마에 optional `timing` 객체 추가, 없으면 null
- **Gemini-driven scoring** — 팩터 목록과 가중치를 프롬프트에 명시하여 일관성 확보
- **Graceful degradation** — timing 파싱 실패 시 기존 시그널만 반환, 에러 노출 없음
- **Single source** — 타이밍 판정도 같은 Gemini 호출에서 생성, 별도 호출 없음

---

## 2. Architecture

### 2.1 Data Flow

```
┌───────────────────────────────────────────────────────────────┐
│  AiSignalService.getSignal(ticker)                            │
│  ────────────────────────────────────────────────────────────│
│  1. ContextAssembler.assemble(ticker)      [변경 없음]         │
│  2. PromptBuilder.systemPrompt()           [timing 스키마 추가] │
│  3. PromptBuilder.userPrompt(context)      [변경 없음]         │
│  4. GeminiLlmClient.generate(...)          [변경 없음]         │
│  5. ResponseValidator.validate(rawJson)    [timing 파싱 추가]  │
│  6. → AiSignal(shortTerm, longTerm, timing) [timing 필드 추가] │
└───────────────────────────────────────────────────────────────┘
```

### 2.2 변경 범위 요약

| Layer | File | 변경 내용 |
|-------|------|-----------|
| **Prompt** | `ai-signal.system.txt` | timing JSON 스키마 + 팩터 목록 + 판정 규칙 추가 |
| **Domain** | `TimingVerdict.java` (신규) | timing 도메인 record |
| **Domain** | `TimingFactor.java` (신규) | 개별 팩터 record |
| **Domain** | `AiSignal.java` | `timing` 필드 추가 |
| **Service** | `ResponseValidator.java` | timing 파싱/검증 로직 추가 |
| **API** | `AiSignalController.java` | 변경 없음 (AiSignal 직렬화 자동 반영) |
| **FE Type** | `ai-signal.ts` | `TimingVerdict` 인터페이스 추가 |
| **FE** | `timing-card.tsx` (신규) | 타이밍 히어로 카드 컴포넌트 |
| **FE** | `ai-signal-panel.tsx` | 타이밍 카드 배치 |
| **FE** | `ai-signal-link-card.tsx` | 타이밍 배지 요약 |

---

## 3. Backend Design

### 3.1 Domain Model

```java
// TimingVerdict.java
package com.nowini.ai.domain;

import java.util.List;

public record TimingVerdict(
    Verdict verdict,          // NOW, NOT_YET, UNCERTAIN
    int score,                // 0~100
    List<TimingFactor> factorsMet,
    List<TimingFactor> factorsUnmet,
    String summaryKo,
    String disclaimerKo
) {
    public enum Verdict { NOW, NOT_YET, UNCERTAIN }
}
```

```java
// TimingFactor.java
package com.nowini.ai.domain;

public record TimingFactor(
    String factor,    // "RSI 과매도"
    String detail,    // "RSI 28 — 30 이하 진입 구간"
    int weight        // 5~20 (프롬프트에서 명시)
) {}
```

```java
// AiSignal.java 변경
public record AiSignal(
    String ticker,
    SignalPerspective shortTerm,
    SignalPerspective longTerm,
    TimingVerdict timing,       // ← 추가 (nullable)
    Instant generatedAt,
    String modelName,
    String disclaimer,
    boolean fallback
) { ... }
```

### 3.2 Prompt Schema Extension

`ai-signal.system.txt`에 추가되는 timing 스키마:

```
3-1. In addition to short_term and long_term, also produce a "timing" object
     that assesses whether NOW is a reasonable entry-timing window:

   "timing": {
     "verdict": "NOW" | "NOT_YET" | "UNCERTAIN",
     "score": integer 0-100,
     "factors_met": [
       { "factor": string (Korean, factor name),
         "detail": string (Korean, 1-sentence factual explanation),
         "weight": integer 5-20 }
     ],
     "factors_unmet": [
       { "factor": string, "detail": string, "weight": integer 5-20 }
     ],
     "summary_ko": one-sentence Korean summary of timing assessment,
     "disclaimer_ko": fixed string "진입 조건의 기술적 충족 여부를 정리한 것으로, 투자 판단은 본인의 책임입니다."
   }

   Timing verdict rules:
   - Sum all factors_met weights → met_total
   - Sum all (factors_met + factors_unmet) weights → max_total
   - score = round(met_total / max_total * 100)
   - verdict: score >= 70 → "NOW", score 40-69 → "UNCERTAIN", score < 40 → "NOT_YET"

   Timing factors to evaluate (include ALL in either met or unmet):
   | Factor | Condition for "met" | Weight |
   |--------|---------------------|--------|
   | RSI 과매도 | RSI <= 30 | 15 |
   | MACD 골든크로스 | MACD 히스토그램 양전환 또는 임박 | 15 |
   | 볼린저밴드 하단 | %B <= 0.1 또는 하단 밴드 이탈 후 복귀 | 10 |
   | 52주 저점 근접 | 현재가가 52주 저점 대비 +10% 이내 | 15 |
   | 이동평균 지지 | 현재가 > MA60 또는 MA60에서 반등 | 10 |
   | 거래량 급증 | 최근 거래량 20일 평균 대비 2배+ (추정 가능 시) | 10 |
   | 시장 안정성 | VIX < 20 (context에 VIX 없으면 UNCERTAIN으로 처리) | 10 |
   | 밸류에이션 | P/E가 섹터 평균 이하 또는 애널리스트 목표가 대비 할인 20%+ | 15 |

   Notes:
   - If indicator data is insufficient, mark that factor as unmet with detail explaining why.
   - factors_met + factors_unmet must cover all 8 factors.
   - The timing assessment focuses on ENTRY timing (buying opportunity conditions).
   - NEVER use "매수하세요", "지금 사세요" or any imperative buy phrasing.
   - Use factual language: "조건이 충족됨", "구간에 진입", "기술적으로 관측됨".
```

### 3.3 ResponseValidator 확장

```java
// RawDualSignal에 timing 추가
@JsonIgnoreProperties(ignoreUnknown = true)
private record RawDualSignal(
    RawSignal short_term,
    RawSignal long_term,
    RawTiming timing         // nullable
) {}

@JsonIgnoreProperties(ignoreUnknown = true)
private record RawTiming(
    String verdict,
    Integer score,
    List<RawFactor> factors_met,
    List<RawFactor> factors_unmet,
    String summary_ko,
    String disclaimer_ko
) {}

@JsonIgnoreProperties(ignoreUnknown = true)
private record RawFactor(
    String factor,
    String detail,
    Integer weight
) {}
```

**Validation rules:**
- timing이 null → 정상 (optional). AiSignal.timing = null로 반환
- timing.verdict가 유효하지 않은 enum → timing = null (graceful)
- timing.score 범위 벗어남 → timing = null
- factors_met/unmet이 empty → timing = null
- timing 텍스트도 forbidden terms 검사 대상

### 3.4 AiSignalService 변경

```java
// 기존 AiSignal 생성 부분 수정
return new AiSignal(
    ticker,
    result.shortTerm(),
    result.longTerm(),
    result.timing(),       // ← 추가
    Instant.now(),
    modelName,
    DISCLAIMER,
    false
);
```

`Result` record에 `TimingVerdict timing` 필드 추가.

---

## 4. Frontend Design

### 4.1 Type Definition

```typescript
// ai-signal.ts 추가
export type TimingVerdictType = 'NOW' | 'NOT_YET' | 'UNCERTAIN';

export interface TimingFactor {
  factor: string;
  detail: string;
  weight: number;
}

export interface TimingVerdict {
  verdict: TimingVerdictType;
  score: number;
  factorsMet: TimingFactor[];
  factorsUnmet: TimingFactor[];
  summaryKo: string;
  disclaimerKo: string;
}

// AiSignal에 추가
export interface AiSignal {
  // ... existing fields
  timing?: TimingVerdict | null;
}
```

### 4.2 TimingCard Component

파일: `apps/web/src/features/stock-detail/ai-signal/components/timing-card.tsx`

```
┌─────────────────────────────────────────────────────────┐
│  🟢 지금이니?!                         조건 충족도 72%   │
│  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ (프로그레스바)     │
│                                                         │
│  ✓ RSI 과매도 (28)              ✗ 시장 안정성 (VIX 28)  │
│  ✓ 52주 저점 근접 (+5%)         ✗ 이동평균 아래          │
│  ✓ 거래량 급증 (2.3배)          ✗ MACD 음전환 유지       │
│  ✓ 밸류에이션 할인 (P/E 12)                             │
│  ✓ 볼린저밴드 하단                                      │
│                                                         │
│  "기술적 저점 신호가 감지되나, 시장 환경 불안으로          │
│   분할 접근 구간으로 관측됩니다"                          │
│                                                         │
│  ⚠️ 진입 조건의 기술적 충족 여부를 정리한 것으로,         │
│     매수 추천이 아닙니다.                                │
└─────────────────────────────────────────────────────────┘
```

**Verdict별 스타일:**

| Verdict | 라벨 | 색상 | 아이콘 |
|---------|------|------|--------|
| NOW | "지금이니?!" | emerald (success) | 채워진 원 |
| UNCERTAIN | "흠.. 애매한데?" | amber (warning) | 물결 |
| NOT_YET | "아직인듯?" | zinc (muted) | 빈 원 |

### 4.3 AI 분석 페이지 배치

```tsx
// ai-signal-page-view.tsx
<AiSignalPageView ticker={ticker}>
  {data.timing && <TimingCard timing={data.timing} />}  {/* ← 최상단 */}
  <AiSignalPanel ticker={ticker} />
</AiSignalPageView>
```

### 4.4 종목 상세 링크 카드 배지

`ai-signal-link-card.tsx`에 타이밍 요약 배지 추가:

```tsx
// 기존 AI 분석 링크 카드 내부
{timing && (
  <span className={cn("rounded-md px-2 py-0.5 text-xs font-bold", verdictBadgeCls)}>
    {verdictLabel}
  </span>
)}
```

---

## 5. API Response Schema

### 5.1 변경된 응답 예시

```json
GET /api/v1/stocks/AAPL/ai-signal

{
  "ticker": "AAPL",
  "shortTerm": { ... },
  "longTerm": { ... },
  "timing": {
    "verdict": "NOW",
    "score": 72,
    "factorsMet": [
      { "factor": "RSI 과매도", "detail": "RSI 28 — 30 이하 진입 구간", "weight": 15 },
      { "factor": "52주 저점 근접", "detail": "현재가 $142, 52주 저점 $138 대비 +3%", "weight": 15 },
      { "factor": "거래량 급증", "detail": "금일 거래량 20일 평균 대비 2.3배", "weight": 10 },
      { "factor": "밸류에이션", "detail": "P/E 18, 섹터 평균 24 대비 할인", "weight": 15 },
      { "factor": "볼린저밴드 하단", "detail": "%B 0.05 — 하단 밴드 접촉", "weight": 10 }
    ],
    "factorsUnmet": [
      { "factor": "이동평균 지지", "detail": "현재가 MA60($155) 아래", "weight": 10 },
      { "factor": "시장 안정성", "detail": "VIX 28 — 불안정 구간", "weight": 10 },
      { "factor": "MACD 골든크로스", "detail": "히스토그램 음수 유지", "weight": 15 }
    ],
    "summaryKo": "기술적 저점 신호가 감지되나, 시장 환경 불안으로 분할 접근 구간으로 관측됩니다",
    "disclaimerKo": "진입 조건의 기술적 충족 여부를 정리한 것으로, 투자 판단은 본인의 책임입니다."
  },
  "generatedAt": "2026-05-04T12:00:00Z",
  "modelName": "gemini-2.5-flash",
  "disclaimer": "...",
  "fallback": false
}
```

### 5.2 하위 호환

- `timing`이 null인 경우 FE에서 타이밍 카드를 숨김
- 기존 클라이언트는 `timing` 필드를 무시 (JSON deserialization에서 unknown field 무시)

---

## 6. Error Handling & Fallback

| 시나리오 | 처리 |
|----------|------|
| Gemini가 timing 필드 누락 | AiSignal.timing = null, 기존 시그널 정상 반환 |
| timing.verdict 파싱 실패 | timing = null |
| timing.score 범위 벗어남 (< 0 or > 100) | timing = null |
| factors_met + factors_unmet < 4개 | timing = null (데이터 불충분) |
| timing 텍스트에 금지어 포함 | timing = null + 메트릭 기록 |
| AiSignal 전체 fallback (rate limit) | timing = null (fallback 시그널에는 포함 안 함) |

---

## 7. Implementation Order

| Step | Layer | Task | Files |
|------|-------|------|-------|
| 1 | BE | 도메인 모델 추가 | `TimingVerdict.java`, `TimingFactor.java` |
| 2 | BE | AiSignal record 수정 | `AiSignal.java` |
| 3 | BE | 프롬프트 스키마 확장 | `ai-signal.system.txt` |
| 4 | BE | ResponseValidator timing 파싱 | `ResponseValidator.java` |
| 5 | BE | AiSignalService 연결 | `AiSignalService.java` |
| 6 | BE | 빌드 검증 | `./gradlew build` |
| 7 | FE | 타입 정의 추가 | `ai-signal.ts` |
| 8 | FE | TimingCard 컴포넌트 | `timing-card.tsx` (신규) |
| 9 | FE | AI 페이지 통합 | `ai-signal-page-view.tsx` |
| 10 | FE | 링크 카드 배지 | `ai-signal-link-card.tsx` |
| 11 | FE | 빌드 검증 | `make web-check` |

---

## 8. Testing Strategy

| 항목 | 방법 |
|------|------|
| ResponseValidator timing 파싱 | 유효/무효 JSON fixture 단위 테스트 |
| 프롬프트 검증 | 실제 Gemini 호출 → timing 필드 존재 확인 (수동) |
| FE 렌더링 | timing null/NOW/NOT_YET/UNCERTAIN 각 케이스 시각 확인 |
| 면책 준수 | ForbiddenTermsRegistry가 timing 텍스트도 스캔하는지 확인 |
| 하위 호환 | timing 없는 응답에서 기존 UI 정상 동작 확인 |
