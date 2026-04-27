# ai-signal-ux Design Document

> **Version**: 0.1
> **Created**: 2026-04-24
> **Author**: wonseok-han
> **Status**: Draft
> **Planning Doc**: [ai-signal-ux.plan.md](../../01-plan/features/ai-signal-ux.plan.md)

---

## 1. Overview

AI 시그널 패널 UX 명확화 + 차트 tf 분리 + 단기/장기 듀얼 관점 동시 제공.

### 1.1 Goals

- 시그널 라벨·용어 명확화로 초보자 오해 방지 + 법적 컴플라이언스
- 차트 타임프레임과 AI 시그널 디커플링 → 중복 Gemini 호출 제거
- Gemini 1회 호출로 단기(기술 지표) + 장기(펀더멘탈) 두 관점 동시 출력

---

## 2. File Change Map

```
apps/api/src/main/java/com/aistockadvisor/
├── ai/
│   ├── domain/
│   │   ├── AiSignal.java                    (전면 재구조 → DualPerspective 래퍼)
│   │   ├── SignalPerspective.java            (신규 — 단일 관점 record)
│   │   ├── IndicatorInterpretation.java      (기존 유지)
│   │   ├── NewsImpact.java                   (기존 유지)
│   │   └── ImpactDirection.java              (기존 유지)
│   ├── service/
│   │   ├── AiSignalService.java              (수정 — tf 제거, v3 캐시 키)
│   │   ├── ContextAssembler.java             (수정 — analyst + 52주 고저 추가)
│   │   ├── PromptBuilder.java                (기존 유지 — 프롬프트 파일만 변경)
│   │   └── ResponseValidator.java            (수정 — v3 듀얼 스키마 파싱)
│   └── web/
│       └── AiSignalController.java           (수정 — tf 파라미터 제거)
└── stock/
    └── service/
        └── StockDetailService.java           (기존 유지 — analystEstimates 이미 존재)

apps/api/src/main/resources/prompts/
└── ai-signal.system.txt                      (전면 재작성 — v3 듀얼 스키마)

apps/web/src/
├── features/stock-detail/
│   ├── ai-signal/
│   │   ├── ai-signal-panel.tsx               (수정 — 듀얼 관점 렌더링 + 라벨/용어)
│   │   ├── components/
│   │   │   ├── signal-guide.tsx              (신규 — 해석 가이드)
│   │   │   ├── confidence-tooltip.tsx        (신규 — 분석 확신도 툴팁)
│   │   │   ├── beginner-explanation.tsx       (기존 유지)
│   │   │   ├── indicator-interpretation.tsx   (기존 유지)
│   │   │   ├── news-impact.tsx               (기존 유지)
│   │   │   ├── what-to-watch.tsx             (기존 유지)
│   │   │   └── collapsible-section.tsx        (기존 유지)
│   │   └── hooks/
│   │       └── use-ai-signal.ts              (수정 — tf 제거)
│   ├── components/
│   │   ├── ai-accuracy-badge.tsx             (수정 — aria-label 용어)
│   │   └── ai-accuracy-tooltip.tsx           (기존 유지)
│   └── stock-detail-view.tsx                 (수정 — AI 시그널에 tf 미전달)
├── lib/api/
│   └── ai-signal.ts                          (수정 — tf 파라미터 제거)
└── types/
    └── ai-signal.ts                          (수정 — v3 듀얼 타입)
```

---

## 3. Backend Design

### 3.1 도메인 모델 (v3)

**기존 AiSignal (v2):** 단일 관점

**신규 구조:**

```java
// SignalPerspective.java (신규)
public record SignalPerspective(
    Signal signal,
    double confidence,
    List<String> rationale,
    List<String> risks,
    String summaryKo,
    String beginnerExplanation,
    List<IndicatorInterpretation> indicatorInterpretation,
    List<NewsImpact> newsImpact,
    List<String> whatToWatch
) {
    public enum Signal { STRONG_BUY, BUY, NEUTRAL, SELL, STRONG_SELL }
}

// AiSignal.java (재구조)
public record AiSignal(
    String ticker,
    SignalPerspective shortTerm,
    SignalPerspective longTerm,
    Instant generatedAt,
    String modelName,
    String disclaimer,
    boolean fallback
) {}
```

### 3.2 ContextAssembler 확장

```java
public Map<String, Object> assemble(String ticker) {  // tf 파라미터 제거
    // 기존 병렬 호출
    Future<StockProfile> pF = ...;
    Future<Quote> qF = ...;
    Future<IndicatorSnapshot> iF = ...;
    Future<List<NewsItem>> nF = ...;
    // 신규 병렬 호출
    Future<AnalystEstimates> aF = ex.submit(() ->
        safely(() -> yahooFinance.analystEstimates(ticker)));

    Map<String, Object> ctx = new LinkedHashMap<>();
    ctx.put("ticker", ticker);
    ctx.put("profile", profileOf(await(pF)));
    ctx.put("quote", quoteOf(await(qF)));       // 52주 고저 포함
    ctx.put("indicators", indicatorsOf(await(iF)));
    ctx.put("recent_news", newsOf(await(nF)));
    ctx.put("analyst_estimates", analystOf(await(aF)));  // 신규
    return ctx;
}

private Map<String, Object> analystOf(AnalystEstimates a) {
    if (a == null) return null;
    Map<String, Object> m = new LinkedHashMap<>();
    if (a.rating() != null) {
        m.put("consensus_score", a.rating().score());
        m.put("consensus_label", a.rating().labelKo());
        m.put("total_analysts", a.rating().totalAnalysts());
    }
    if (a.priceTarget() != null) {
        m.put("target_mean", a.priceTarget().mean());
        m.put("target_high", a.priceTarget().high());
        m.put("target_low", a.priceTarget().low());
        m.put("upside_percent", a.priceTarget().upsidePercent());
    }
    if (a.earnings() != null && !a.earnings().isEmpty()) {
        m.put("recent_earnings", a.earnings().stream().map(eq -> {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("quarter", eq.quarter());
            e.put("eps_actual", eq.epsActual());
            e.put("eps_estimate", eq.epsEstimate());
            e.put("result", eq.result());
            return e;
        }).toList());
    }
    return m.isEmpty() ? null : m;
}
```

기존 `quoteOf()`에 52주 고저 추가:

```java
private Map<String, Object> quoteOf(Quote q) {
    // ... 기존 필드 ...
    m.put("fifty_two_week_high", q.fiftyTwoWeekHigh());  // 신규
    m.put("fifty_two_week_low", q.fiftyTwoWeekLow());    // 신규
    return m;
}
```

### 3.3 프롬프트 v3 (ai-signal.system.txt)

핵심 변경: 응답을 `short_term` + `long_term` 두 객체로 분리.

```
Output MUST be valid JSON matching this schema (v3):
{
  "short_term": {
    "signal": "STRONG_BUY"|"BUY"|"NEUTRAL"|"SELL"|"STRONG_SELL",
    "confidence": 0.0~1.0,
    "rationale": [...],
    "risks": [...],
    "summary_ko": "...",
    "beginner_explanation": "...",
    "indicator_interpretation": [...],
    "news_impact": [...],
    "what_to_watch": [...]
  },
  "long_term": {
    // 동일 구조
  }
}

Analysis guidelines:
- short_term (1~2주): 기술 지표(RSI, MACD, 볼린저, MA5/20)와 최근 뉴스 중심.
  가격 모멘텀과 단기 수급 관점에서 분석.
- long_term (6개월~1년): 펀더멘탈 중심.
  analyst_estimates(목표가, 컨센서스, EPS 실적), P/E, 시총, 섹터 위치,
  52주 고저 대비 현재가, 장기 이동평균(MA60) 관점에서 분석.
  analyst_estimates가 null이면 기술적 장기 추세 + 기업 기본 정보로 분석하고
  "애널리스트 커버리지가 없어 기술적/기본적 지표 위주로 분석했습니다" 명시.
```

### 3.4 ResponseValidator v3

```java
@JsonIgnoreProperties(ignoreUnknown = true)
private record RawDualSignal(
    RawSignal short_term,
    RawSignal long_term
) {}
```

- `RawDualSignal`로 파싱 → `short_term` / `long_term` 각각 기존 `RawSignal` 로직으로 검증
- 둘 중 하나라도 검증 실패 → 전체 invalid → fallback neutral

### 3.5 AiSignalService

```java
public AiSignal getSignal(String ticker) {  // tf 파라미터 제거
    String cacheKey = "ai:" + ticker + ":v3";
    // ... 나머지 동일, ctx에서 timeframe 제거
    Map<String, Object> ctx = contextAssembler.assemble(ticker);
    // ...
}
```

### 3.6 AiSignalController

```java
@GetMapping("/{ticker}/ai-signal")
public AiSignal signal(
    @PathVariable("ticker") @Pattern(regexp = TICKER_REGEX) String ticker) {
    return service.getSignal(ticker);
}
// tf 쿼리 파라미터 완전 제거
```

### 3.7 AiSignalAuditEntity

`timeframe` 컬럼: 기존 audit 데이터와 호환을 위해 컬럼은 유지하되 null로 저장. 기존 audit 레코드는 그대로 보존.

### 3.8 Fallback (v3)

```java
private AiSignal fallback(String ticker) {
    SignalPerspective neutral = new SignalPerspective(
        Signal.NEUTRAL, 0.5,
        List.of("현재 충분한 데이터를 종합할 수 없어 중립 관점으로 제시합니다."),
        List.of("시장 변동성에 따라 가격 방향이 크게 바뀔 수 있습니다."),
        "일시적으로 AI 분석이 제한되어 중립(NEUTRAL) 관점으로 제공됩니다.",
        null, null, null, null
    );
    return new AiSignal(ticker, neutral, neutral, Instant.now(),
            modelName, Disclaimers.AI_SIGNAL, true);
}
```

---

## 4. Frontend Design

### 4.1 타입 정의 (ai-signal.ts)

```typescript
export type AiSignalClass =
  | 'STRONG_BUY' | 'BUY' | 'NEUTRAL' | 'SELL' | 'STRONG_SELL';

export interface SignalPerspective {
  signal: AiSignalClass;
  confidence: number;
  rationale: string[];
  risks: string[];
  summaryKo: string;
  beginnerExplanation?: string | null;
  indicatorInterpretation?: IndicatorInterpretation[] | null;
  newsImpact?: NewsImpact[] | null;
  whatToWatch?: string[] | null;
}

export interface AiSignal {
  ticker: string;
  shortTerm: SignalPerspective;
  longTerm: SignalPerspective;
  generatedAt: string;
  modelName: string;
  disclaimer: string;
  fallback: boolean;
}
```

### 4.2 API 클라이언트 (ai-signal.ts)

```typescript
export function getAiSignal(ticker: string): Promise<AiSignal> {
  const t = encodeURIComponent(ticker);
  return apiFetch<AiSignal>(`/stocks/${t}/ai-signal`);  // tf 제거
}
```

### 4.3 Hook (use-ai-signal.ts)

```typescript
export function useAiSignal(ticker: string) {
  return useQuery<AiSignal>({
    queryKey: ['ai-signal', ticker],  // tf 제거
    queryFn: () => getAiSignal(ticker),
    staleTime: 10 * 60 * 1000,
  });
}
```

### 4.4 stock-detail-view.tsx

```diff
- <AiSignalPanel ticker={ticker} tf={tf} />
+ <AiSignalPanel ticker={ticker} />
```

### 4.5 ai-signal-panel.tsx 레이아웃

```
┌─────────────────────────────────────────────┐
│ AI 참고 분석                                │
├─────────────────────────────────────────────┤
│ 📖 시그널 해석 가이드 (접이식, 기본 닫힘)    │
├─────────────────────────────────────────────┤
│ ┌─── 단기 관점 (1~2주) ──────────────────┐  │
│ │ [강한 상승 신호]    분석 확신도 72% (?) │  │
│ │ ████████████████████░░░░░░░░           │  │
│ │ 정합도 배지                             │  │
│ │ 요약 텍스트...                          │  │
│ │ ▸ 쉽게 이해하기                        │  │
│ │ ▸ 지표 해석                            │  │
│ │ ▸ 뉴스 영향                            │  │
│ │ ▸ 관전 포인트                          │  │
│ │ 근거 | 리스크                          │  │
│ └────────────────────────────────────────┘  │
│ ┌─── 장기 관점 (6개월~1년) ──────────────┐  │
│ │ [중립]              분석 확신도 55% (?) │  │
│ │ ███████████░░░░░░░░░░░░░░░░░           │  │
│ │ 정합도 배지                             │  │
│ │ 요약 텍스트...                          │  │
│ │ ▸ 쉽게 이해하기                        │  │
│ │ ▸ 지표 해석                            │  │
│ │ ▸ 뉴스 영향                            │  │
│ │ ▸ 관전 포인트                          │  │
│ │ 근거 | 리스크                          │  │
│ └────────────────────────────────────────┘  │
│ 면책 고지 + 모델명 + 생성시각               │
└─────────────────────────────────────────────┘
```

### 4.6 컴포넌트 구조

```typescript
function AiSignalContent({ ticker }: { ticker: string }) {
  const { data, isLoading, error } = useAiSignal(ticker);
  // ...
  return (
    <section className="card brand-glow p-5">
      <h2>AI 참고 분석</h2>
      <SignalGuide />
      {data.fallback && <FallbackBanner />}
      <PerspectiveSection label="단기 관점 (1~2주)" perspective={data.shortTerm} />
      <PerspectiveSection label="장기 관점 (6개월~1년)" perspective={data.longTerm} />
      <Footer disclaimer={data.disclaimer} model={data.modelName} at={data.generatedAt} />
    </section>
  );
}

function PerspectiveSection({ label, perspective }: {
  label: string;
  perspective: SignalPerspective;
}) {
  return (
    <CollapsibleSection title={label} defaultOpen={true}>
      <SignalHero signal={perspective.signal} confidence={perspective.confidence} />
      <AiAccuracyBadge window={30} />
      <p>{perspective.summaryKo}</p>
      <BeginnerExplanation text={perspective.beginnerExplanation} />
      <IndicatorInterpretation items={perspective.indicatorInterpretation} />
      <NewsImpact items={perspective.newsImpact} />
      <WhatToWatch items={perspective.whatToWatch} />
      <RationaleRisks rationale={perspective.rationale} risks={perspective.risks} />
    </CollapsibleSection>
  );
}
```

### 4.7 시그널 라벨 매핑 (mapSignal)

```typescript
function mapSignal(signal: AiSignalClass) {
  switch (signal) {
    case 'STRONG_BUY': return { label: '강한 상승 신호', ... };
    case 'BUY':        return { label: '상승 신호', ... };
    case 'NEUTRAL':    return { label: '중립', ... };
    case 'SELL':       return { label: '하락 신호', ... };
    case 'STRONG_SELL': return { label: '강한 하락 신호', ... };
  }
}
```

### 4.8 분석 확신도 툴팁 (confidence-tooltip.tsx)

```typescript
function ConfidenceTooltip() {
  // hover/focus로 토글
  return (
    <span className="...">
      AI가 자체 분석에 대해 느끼는 확신 수준입니다.
      주가 예측 확률이 아닙니다.
    </span>
  );
}
```

SignalHero에서:
```diff
- 신뢰도 <span>{pct}%</span>
+ 분석 확신도 <span>{pct}%</span> <ConfidenceTooltip />
```

### 4.9 시그널 해석 가이드 (signal-guide.tsx)

```typescript
function SignalGuide() {
  return (
    <CollapsibleSection title="이 분석은 이렇게 읽으세요" defaultOpen={false}>
      <ul>
        <li>시그널은 시장 데이터·기술 지표·뉴스를 종합한 AI의 방향성 요약입니다</li>
        <li>투자 추천이 아니며, 하나의 참고 관점으로 활용해 주세요</li>
        <li>분석 확신도는 AI가 자체 판단에 느끼는 확신이며, 주가 예측 확률이 아닙니다</li>
        <li>방향 일치율은 과거 시그널이 실제 주가 방향과 얼마나 일치했는지 보여줍니다</li>
      </ul>
    </CollapsibleSection>
  );
}
```

### 4.10 미리보기 텍스트 수정

```diff
- "매수/매도/중립 시그널과 근거를 AI가 분석해 드립니다"
+ "시장 데이터를 종합한 AI 참고 분석을 확인할 수 있습니다"
```

---

## 5. Implementation Order

| Step | 작업 | 파일 | 의존성 |
|------|------|------|--------|
| 1 | `SignalPerspective` record 신규 생성 | `SignalPerspective.java` | 없음 |
| 2 | `AiSignal` v3 재구조 | `AiSignal.java` | Step 1 |
| 3 | `ContextAssembler` 확장 (analyst + 52주 고저) | `ContextAssembler.java` | 없음 |
| 4 | `ai-signal.system.txt` v3 프롬프트 재작성 | `ai-signal.system.txt` | Step 2 |
| 5 | `ResponseValidator` v3 듀얼 파싱 | `ResponseValidator.java` | Step 1 |
| 6 | `AiSignalService` tf 제거 + v3 캐시 키 + fallback | `AiSignalService.java` | Step 2, 3, 5 |
| 7 | `AiSignalController` tf 파라미터 제거 | `AiSignalController.java` | Step 6 |
| 8 | `make api-check` BE 빌드 검증 | — | Step 7 |
| 9 | FE 타입 정의 v3 | `ai-signal.ts` | Step 2 |
| 10 | API 클라이언트 + Hook tf 제거 | `ai-signal.ts`, `use-ai-signal.ts` | Step 9 |
| 11 | 신규 컴포넌트: `signal-guide.tsx`, `confidence-tooltip.tsx` | 신규 파일 2개 | 없음 |
| 12 | `ai-signal-panel.tsx` 전면 리팩터링 (듀얼 관점 + 라벨/용어) | `ai-signal-panel.tsx` | Step 9, 10, 11 |
| 13 | `stock-detail-view.tsx` tf 전달 제거 | `stock-detail-view.tsx` | Step 12 |
| 14 | `ai-accuracy-badge.tsx` aria-label 수정 | `ai-accuracy-badge.tsx` | 없음 |
| 15 | `make web-check` FE 빌드 검증 | — | Step 14 |

---

## 6. Cache Strategy

| 키 | TTL | 변경 |
|---|---|---|
| `ai:{ticker}:v3` | 60분 (기존 동일) | v2 → v3 키 변경, tf 분기 제거 |
| `yahoo:summary:{ticker}` | 24시간 | 기존 유지 (analyst 데이터 포함) |

기존 `ai:{ticker}:{tf}:v2` 캐시는 TTL 만료 후 자동 정리.

---

## 7. API Contract

### Request

```
GET /api/v1/stocks/{ticker}/ai-signal
```

tf 파라미터 제거. 기존 클라이언트가 `?tf=1D` 를 보내도 무시 (하위 호환).

### Response (v3)

```json
{
  "ticker": "AAPL",
  "shortTerm": {
    "signal": "BUY",
    "confidence": 0.72,
    "rationale": ["RSI 55로 과매수 아닌 상승 구간", "..."],
    "risks": ["단기 지지선 이탈 시 하방 압력", "..."],
    "summaryKo": "...",
    "beginnerExplanation": "...",
    "indicatorInterpretation": [...],
    "newsImpact": [...],
    "whatToWatch": [...]
  },
  "longTerm": {
    "signal": "NEUTRAL",
    "confidence": 0.55,
    "rationale": ["애널리스트 목표가 대비 현재 가격이 근접", "..."],
    "risks": ["금리 인하 지연 시 성장주 멀티플 축소", "..."],
    "summaryKo": "...",
    "beginnerExplanation": "...",
    "indicatorInterpretation": [...],
    "newsImpact": [...],
    "whatToWatch": [...]
  },
  "generatedAt": "2026-04-24T10:30:00Z",
  "modelName": "gemini-2.5-flash",
  "disclaimer": "...",
  "fallback": false
}
```

---

## 8. Graceful Degradation

| 시나리오 | 처리 |
|---|---|
| 애널리스트 데이터 없음 (소형주) | `analyst_estimates: null` → 프롬프트가 기술적/기본적 지표로 장기 분석 + 고지 문구 |
| Gemini 호출 실패 | 단기/장기 모두 NEUTRAL fallback |
| v3 파싱 실패 (한쪽만 유효) | 전체 invalid → fallback (부분 응답 허용 안 함) |
| 기존 FE가 `?tf=` 전송 | BE에서 무시 (하위 호환) |

---

## 9. Testing Checklist

- [ ] BE: `make api-check` 통과
- [ ] FE: `make web-check` 통과
- [ ] 대형주 (AAPL 등) — 단기/장기 두 관점 정상 렌더링
- [ ] 소형주 (analyst 데이터 없는 종목) — 장기 관점 graceful degradation 확인
- [ ] 차트 탭 전환 시 AI 시그널 재조회 **안 됨** 확인
- [ ] 미인증 미리보기 금지 용어 0건
- [ ] 다크/라이트/브랜드 3테마 렌더링
- [ ] CI forbidden-terms 워크플로우 통과

---

## 10. Coding Conventions

### 10.1 BE JSON 필드명

API 응답은 `camelCase` (Spring Boot 기본): `shortTerm`, `longTerm`, `summaryKo`.
프롬프트 내 LLM JSON 스키마는 `snake_case`: `short_term`, `long_term`, `summary_ko`.
ResponseValidator가 snake_case → camelCase 변환 담당.

### 10.2 FE 파일명

신규 파일: `signal-guide.tsx`, `confidence-tooltip.tsx` (kebab-case 컨벤션).
