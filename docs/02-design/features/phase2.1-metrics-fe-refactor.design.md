# phase2.1-metrics-fe-refactor Design Document

> **Feature**: phase2.1-metrics-fe-refactor
> **Version**: 0.2.1
> **Status**: Draft
> **Date**: 2026-04-14
> **Author**: wonseok-han
> **Depends On**: `docs/01-plan/features/phase2.1-metrics-fe-refactor.plan.md`
> **References**: `docs/archive/2026-04/phase2-rag-pipeline/phase2-rag-pipeline.design.md` §5.3, §13.2

---

## 1. Goal (한 줄)

Phase 2 잔여 Major gap 2건(**FR-15 Micrometer counter 미구현** + **FE 폴더 편차**)을 설계-구현 완전 동기화 방식으로 해소해 Match Rate 93% → **95%+**, 운영 관측성(Observability) 기반 확보.

---

## 2. Architecture Overview

```
                                  ┌────────────────────────────────────────────┐
                                  │  Micrometer MeterRegistry (Prometheus)     │
                                  │                                            │
                   ┌──[ 주입 ]────┤  Counter: llm.call.count                    │
                   │              │  Counter: llm.token.total                  │
                   │              │  Counter: llm.failure.count                │
                   │              │  Counter: llm.forbidden.hit.count          │
                   │              │  Timer  : llm.call.latency (NEW — 파생)    │
                   │              └────────────┬───────────────────────────────┘
                   │                           │
┌──────────────────┴──────┐   ┌────────────────┴────────┐   ┌──────────────────┐
│ GeminiLlmClient         │   │ ResponseValidator       │   │ LegalGuardFilter │
│  - call.count / token   │   │  - failure.count        │   │  - forbidden.hit │
│  - failure(timeout)     │   │  - forbidden.hit        │   │    (layer=filter)│
│  - call.latency (Timer) │   │    (layer=validator)    │   │                  │
└─────────────────────────┘   └─────────────────────────┘   └──────────────────┘
                                          │
                              scrape ◄────┘
                 ┌─────────────────────────────────────┐
                 │ /actuator/prometheus (allowlist)    │
                 │ health, info, metrics, prometheus   │
                 └─────────────────────────────────────┘
```

**FE 영역** (독립 변경):

```
apps/web/src/features/
├── search/                          ← 유지
├── stock-detail/
│   ├── chart/                       ← 유지
│   ├── indicators/                  ← 유지
│   ├── hooks/                       ← 유지
│   ├── news/            ◄── NEW (← features/news/)
│   │   ├── news-panel.tsx
│   │   └── hooks/use-news.ts
│   └── ai-signal/       ◄── NEW (← features/ai-signal/)
│       ├── ai-signal-panel.tsx
│       └── hooks/use-ai-signal.ts
```

---

## 3. Metric Schema (핵심 설계)

### 3.1 지표 4종 + Timer 1종

| Metric | Type | Unit | Tags | 주입 지점 | 증가 시점 |
|---|---|---|---|---|---|
| `llm.call.count` | Counter | 1 | `feature`={news\|ai-signal}, `model`=gemini-flash | `GeminiLlmClient#generate` 진입 | 매 호출 직전 (성공/실패 무관) |
| `llm.token.total` | Counter | tokens | `direction`={input\|output}, `model`=gemini-flash | `GeminiLlmClient#generate` 응답 파싱 후 | `usageMetadata` 파싱 성공 시 2회 increment (in, out) |
| `llm.failure.count` | Counter | 1 | `feature`, `reason`={timeout\|http\|parse\|validation\|forbidden} | `GeminiLlmClient` catch + `ResponseValidator` invalid | 각 실패 분류별 |
| `llm.forbidden.hit.count` | Counter | 1 | `layer`={validator\|filter}, `feature` (optional) | `ResponseValidator` + `LegalGuardFilter` 검출 시 | 검출당 1 |
| `llm.call.latency` | Timer | ms | `feature`, `outcome`={success\|failure} | `GeminiLlmClient#generate` wrapping | 매 호출 (latency 분포 히스토그램) |

### 3.2 Tag Cardinality 가드

**절대 tag 로 넣지 않음**:
- `ticker` (수천 심볼 → 카디널리티 폭발)
- `userId`, `requestId`, `traceId` (고유값)
- 원문 에러 메시지, stack trace

**허용 tag 값 (enum 화 강제)**:
- `feature` ∈ {`news`, `ai-signal`} — 정확히 2개
- `model` ∈ {`gemini-flash`} — 현재 1개, 확장 시 추가
- `direction` ∈ {`input`, `output`} — 2개
- `reason` ∈ {`timeout`, `http`, `parse`, `validation`, `forbidden`} — 5개
- `layer` ∈ {`validator`, `filter`} — 2개
- `outcome` ∈ {`success`, `failure`} — 2개

이론 최대 카디널리티: 2×1 + 2×1 + 2×5 + 2×2 + 2×2 = **22 시계열**. Prometheus 부담 없음.

### 3.3 명명 규칙

- 전부 **dot-notation 소문자** (Micrometer 컨벤션, Prometheus export 시 `llm_call_count` 로 자동 변환)
- prefix `llm.` 로 도메인 구분 → 향후 `news.fetch.count`, `cache.hit.count` 등 확장 여지 확보
- 불린성 지표(예: fallback 발생)는 별도 metric 대신 `llm.failure.count{reason=...}` 에 흡수

---

## 4. Component Design (BE)

### 4.1 MetricsConfig (NEW)

**경로**: `apps/api/src/main/java/com/aistockadvisor/common/metrics/MetricsConfig.java`

**책임**:
- `SimpleMeterRegistry` 는 Spring Boot auto-config 가 제공 (actuator + micrometer-registry-prometheus 존재 시 `PrometheusMeterRegistry` 자동 주입)
- 공통 상수 (metric 이름, tag key) 만 `LlmMetrics` 유틸 클래스로 모음
- **별도 Config bean 불필요** — 생성자 주입만으로 충분

**경로**: `apps/api/src/main/java/com/aistockadvisor/common/metrics/LlmMetrics.java`

```java
public final class LlmMetrics {
    public static final String CALL_COUNT = "llm.call.count";
    public static final String TOKEN_TOTAL = "llm.token.total";
    public static final String FAILURE_COUNT = "llm.failure.count";
    public static final String FORBIDDEN_HIT = "llm.forbidden.hit.count";
    public static final String CALL_LATENCY = "llm.call.latency";

    public static final String TAG_FEATURE = "feature";
    public static final String TAG_MODEL = "model";
    public static final String TAG_DIRECTION = "direction";
    public static final String TAG_REASON = "reason";
    public static final String TAG_LAYER = "layer";
    public static final String TAG_OUTCOME = "outcome";

    private LlmMetrics() {}
}
```

### 4.2 GeminiLlmClient 수정

**현재**: `generate(systemPrompt, userPrompt)` 가 `LlmResult(content, modelName, tokensIn, tokensOut, latencyMs)` 반환.

**변경 지점**:
1. 생성자에 `MeterRegistry meterRegistry` 주입
2. `feature` 구분을 위해 `generate(systemPrompt, userPrompt, feature)` 오버로드 추가 (기존 메서드는 `feature="unknown"` 기본으로 유지)
3. 호출 흐름:
   ```
   [진입] counter(CALL_COUNT, feature, model).increment()
   [try]  WebClient 호출
         응답 파싱 시:
         counter(TOKEN_TOTAL, direction=input, model).increment(tokensIn)
         counter(TOKEN_TOTAL, direction=output, model).increment(tokensOut)
         timer(CALL_LATENCY, feature, outcome=success).record(duration)
   [catch WebClientResponseException]
         counter(FAILURE_COUNT, feature, reason=http).increment()
         timer(CALL_LATENCY, feature, outcome=failure).record(duration)
         (기존 예외 재throw)
   [catch TimeoutException / ReadTimeoutException]
         counter(FAILURE_COUNT, feature, reason=timeout).increment()
   [catch JsonProcessing/IO]
         counter(FAILURE_COUNT, feature, reason=parse).increment()
   ```
4. `LlmResult` 레코드는 **변경 없음** (기존 tokensIn/tokensOut 필드가 이미 있음 → 상위 소비자 영향 0)

**Feature 전달 방식**: 상위 호출자(`AiSignalService`, `NewsTranslator`)가 `feature="ai-signal"` 또는 `"news"` 를 인자로 명시. 기본 오버로드는 backward-compat 용.

### 4.3 ResponseValidator 수정

**변경 지점**:
1. 생성자에 `MeterRegistry` 주입
2. `validate(rawJson, feature)` 오버로드 추가 (기본은 `feature="ai-signal"`, 현재 유일 호출자)
3. 증가 지점:
   - `Result.invalid("forbidden-terms-detected", hits, rawMap)` 반환 전:
     ```
     counter(FORBIDDEN_HIT, layer=validator, feature).increment(hits.size())
     counter(FAILURE_COUNT, feature, reason=forbidden).increment()
     ```
   - 기타 invalid 경로 (parse/enum/confidence/rationale blank):
     ```
     counter(FAILURE_COUNT, feature, reason=validation).increment()
     ```

### 4.4 LegalGuardFilter 수정

**변경 지점**:
1. 생성자에 `MeterRegistry` 주입
2. `sanitize(...)` 내부에서 금지용어 치환 직전:
   ```
   counter(FORBIDDEN_HIT, layer=filter).increment(hits.size())
   ```
   - `feature` tag 는 **생략** — path 기반(`/ai-signal`, `/news`) 유추보다 `layer=filter` 단일 집계가 노이즈 적음. 필요 시 Phase 3 에서 path 파싱 추가

### 4.5 의존성 추가 (`apps/api/build.gradle.kts`)

```kotlin
dependencies {
    // 기존 유지
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // NEW
    implementation("io.micrometer:micrometer-registry-prometheus")
    // 나머지 유지
}
```

Spring Boot 3.5.13 BOM 이 `micrometer-registry-prometheus` 버전 관리 → 명시 버전 불필요.

### 4.6 `application.yml` 수정

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus   # ← prometheus 추가
  endpoint:
    health:
      probes:
        enabled: true
      show-details: never
    prometheus:
      access: read_only                           # ← NEW (SB 3.5 신규 access 속성)
  metrics:
    tags:
      application: ai-stock-advisor               # ← 공통 tag
  prometheus:
    metrics:
      export:
        enabled: true
```

**보안**:
- `env`, `beans`, `configprops`, `loggers`, `heapdump` 는 expose 하지 **않음**
- 배포 시 reverse-proxy 레벨에서 `/actuator/*` IP allowlist 가능 (Phase 4 재검토)

---

## 5. Test Strategy

### 5.1 신규 테스트: `MicrometerMetricsTest`

**경로**: `apps/api/src/test/java/com/aistockadvisor/ai/MicrometerMetricsTest.java`

**케이스** (8건):

| # | 시나리오 | Assert |
|---|---|---|
| T-1 | GeminiLlmClient 성공 호출 1회 | `llm.call.count{feature=ai-signal,model=gemini-flash}` == 1 |
| T-2 | GeminiLlmClient 성공 호출 + 토큰 (in=100, out=50) | `llm.token.total{direction=input}` == 100, `direction=output` == 50 |
| T-3 | GeminiLlmClient Timeout | `llm.failure.count{reason=timeout}` == 1 |
| T-4 | GeminiLlmClient HTTP 500 | `llm.failure.count{reason=http}` == 1 |
| T-5 | ResponseValidator parse 실패 | `llm.failure.count{reason=validation}` == 1 |
| T-6 | ResponseValidator 금지용어 2건 검출 | `llm.forbidden.hit.count{layer=validator}` == 2, `llm.failure.count{reason=forbidden}` == 1 |
| T-7 | LegalGuardFilter neutral 치환 (금지용어 1건) | `llm.forbidden.hit.count{layer=filter}` == 1 |
| T-8 | latency Timer 기록 (>0ms) | `llm.call.latency` count == 1, max > 0 |

**수단**:
- `SimpleMeterRegistry` 를 각 테스트 `@BeforeEach` 에서 신규 생성 → 싱글톤 오염 차단
- Gemini API 는 `@MockBean LlmClient` 로 교체하거나 `WebClient` mock
- LegalGuardFilter 는 `MockMvc` 통합 테스트 1건

### 5.2 Actuator 엔드포인트 smoke test

`apps/api/src/test/java/com/aistockadvisor/ActuatorExposureTest.java` (NEW, 또는 기존 `ApiApplicationTests` 에 추가):

```java
@Test
void prometheusEndpointExposed() throws Exception {
    mockMvc.perform(get("/actuator/prometheus"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("llm_call_count")));
}

@Test
void sensitiveEndpointsNotExposed() throws Exception {
    mockMvc.perform(get("/actuator/env")).andExpect(status().isNotFound());
    mockMvc.perform(get("/actuator/beans")).andExpect(status().isNotFound());
}
```

### 5.3 기존 테스트 보존

- `RedTeamPromptInjectionTest` 20/20 — ResponseValidator 변경으로 시그니처 호환 유지 필요 → 기본 `feature="ai-signal"` 오버로드 제공해야 기존 테스트 무수정
- `AiSignalServiceTest` 등 기존 테스트 전부 green 유지
- FE: `pnpm --filter web typecheck` + `pnpm --filter web lint` green

### 5.4 수동 smoke

```
make api-dev
curl -s localhost:8080/api/v1/stocks/AAPL/ai-signal?tf=DAY > /dev/null
curl -s localhost:8080/actuator/prometheus | grep -E '^llm_' | head -20
```

---

## 6. FE Refactor Design

### 6.1 이동 매핑표

| From | To | git 명령 |
|---|---|---|
| `apps/web/src/features/news/news-panel.tsx` | `apps/web/src/features/stock-detail/news/news-panel.tsx` | `git mv` |
| `apps/web/src/features/news/hooks/use-news.ts` | `apps/web/src/features/stock-detail/news/hooks/use-news.ts` | `git mv` |
| `apps/web/src/features/ai-signal/ai-signal-panel.tsx` | `apps/web/src/features/stock-detail/ai-signal/ai-signal-panel.tsx` | `git mv` |
| `apps/web/src/features/ai-signal/hooks/use-ai-signal.ts` | `apps/web/src/features/stock-detail/ai-signal/hooks/use-ai-signal.ts` | `git mv` |

### 6.2 Import path 교정표 (사전 grep 확보)

현재 4곳:

| 파일 | 기존 import | 신규 import |
|---|---|---|
| `features/stock-detail/stock-detail-view.tsx:5` | `@/features/ai-signal/ai-signal-panel` | `@/features/stock-detail/ai-signal/ai-signal-panel` |
| `features/stock-detail/stock-detail-view.tsx:6` | `@/features/news/news-panel` | `@/features/stock-detail/news/news-panel` |
| `features/news/news-panel.tsx:3` (→ 이동 후) | `@/features/news/hooks/use-news` | `@/features/stock-detail/news/hooks/use-news` |
| `features/ai-signal/ai-signal-panel.tsx:3` (→ 이동 후) | `@/features/ai-signal/hooks/use-ai-signal` | `@/features/stock-detail/ai-signal/hooks/use-ai-signal` |

**실행 방식**: `git mv` → `sed -i '' -E "s|@/features/news/|@/features/stock-detail/news/|g; s|@/features/ai-signal/|@/features/stock-detail/ai-signal/|g" <file>` → `pnpm --filter web typecheck` 로 누락 검증.

### 6.3 tsconfig 확인

`apps/web/tsconfig.json` 의 `paths` 는 `"@/*": ["./src/*"]` 형태로 되어 있어 **별도 수정 불필요**. baseUrl 기반 경로 해석이 자동 적용.

### 6.4 제약

- **컴포넌트/훅 identifier 유지**: `NewsPanel`, `AiSignalPanel`, `useNews`, `useAiSignal` — 이름 변경 금지 (기능 리팩터 아님)
- **파일명 kebab-case 유지**: 이미 kebab-case, 이동 중 case 변경 금지
- **기능 로직 변경 금지**: Props, state, hook signature 전부 동일. 오로지 위치 이동 + import path

---

## 7. Risks & Mitigations (Plan §5 보강)

| Risk | Mitigation |
|---|---|
| Micrometer 태그 카디널리티 폭발 | §3.2 allowlist 강제, PR 리뷰 시 새 tag 추가 금지 규칙 |
| Actuator 민감정보 노출 (env 등) | §4.6 exposure 화이트리스트 `health,info,metrics,prometheus` 만 |
| ResponseValidator 시그니처 변경으로 RedTeamPromptInjectionTest 깨짐 | §5.3 기본 오버로드 유지, 기존 호출 `validate(rawJson)` 무수정 |
| FE 이동 시 import 누락 | §6.2 사전 grep 결과 4곳만 존재 → sed 일괄 + typecheck 두 번 |
| SimpleMeterRegistry 테스트 싱글톤 오염 | §5.1 각 테스트 @BeforeEach 에서 신규 생성 |
| Gemini usageMetadata null 일 때 token counter NPE | §4.2 null 체크 후 skip (기존 `tokensIn = resp.usageMetadata() == null ? null : ...` 패턴 유지) |

---

## 8. Implementation Order (Do 단계에서 참조)

1. **Step 1**: `build.gradle.kts` 에 `micrometer-registry-prometheus` 추가 → `./gradlew build -x test` 통과 확인
2. **Step 2**: `LlmMetrics.java` 상수 클래스 작성 (no-test)
3. **Step 3**: `application.yml` exposure 업데이트 + `ActuatorExposureTest` 추가 → green
4. **Step 4**: `GeminiLlmClient` 에 MeterRegistry 주입 + counter/timer 호출 삽입 (+ `feature` 오버로드) → `MicrometerMetricsTest` T-1~T-4,T-8 green
5. **Step 5**: `ResponseValidator` 에 MeterRegistry 주입 + counter 삽입 (+ `feature` 오버로드) → T-5,T-6 green
6. **Step 6**: `LegalGuardFilter` 에 MeterRegistry 주입 + counter 삽입 → T-7 green
7. **Step 7**: `AiSignalService`, `NewsTranslator` 호출부 `feature` 인자 전달 (optional — 오버로드 덕분에 후순위 가능)
8. **Step 8**: `make api-check` green + 수동 smoke (`/actuator/prometheus | grep ^llm_`)
9. **Step 9**: FE `git mv` 4건 + sed 일괄 교정 → `pnpm --filter web typecheck && pnpm --filter web lint` green
10. **Step 10**: `make check` 전체 green → 커밋 분할 (BE metrics / FE refactor 각 1건)
11. **Step 11**: `/pdca analyze phase2.1-metrics-fe-refactor` → Match Rate ≥ 95% 확인
12. **Step 12**: PR `feat/phase2.1-metrics-fe-refactor` → CI green → squash merge

---

## 9. Acceptance Criteria (Done)

- [ ] `curl -s localhost:8080/actuator/prometheus | grep -E '^llm_(call|token|failure|forbidden).*_(count|total)' | wc -l` ≥ 8 (4 metric × 평균 2 series)
- [ ] `MicrometerMetricsTest` 8 케이스 전부 pass
- [ ] `ActuatorExposureTest` 2 케이스 pass (prometheus 노출, env/beans 차단)
- [ ] `apps/web/src/features/news`, `apps/web/src/features/ai-signal` 디렉토리 **부재**
- [ ] `apps/web/src/features/stock-detail/news`, `.../ai-signal` 디렉토리 **존재** + panel/hook 전부 이동
- [ ] `grep -rn "@/features/news\|@/features/ai-signal" apps/web/src` → 0 hit
- [ ] `make check` green, 기존 `RedTeamPromptInjectionTest` 20/20 유지
- [ ] Match Rate ≥ 95% (`docs/03-analysis/phase2.1-metrics-fe-refactor.analysis.md`)

---

## 10. Non-Goals (재강조)

- ❌ Prometheus/Grafana 인프라 · AlertManager — 지표 노출만
- ❌ ticker 단위 histogram — 카디널리티 폭발 위험
- ❌ 프롬프트 외부화, 재시도 루프, rationale 3~5개 강제 — Phase 3 로 이관
- ❌ FE 외 리팩터(예: `stock-detail-view.tsx` 분할) — 폴더 이동과 무관한 변경 금지

---

## 11. References

- Phase 2 Design §13.2 Step 20 (Micrometer 원본 설계): `docs/archive/2026-04/phase2-rag-pipeline/phase2-rag-pipeline.design.md`
- Phase 2 Analysis Major gap 4, 5: `docs/archive/2026-04/phase2-rag-pipeline/phase2-rag-pipeline.analysis.md`
- Plan: `docs/01-plan/features/phase2.1-metrics-fe-refactor.plan.md`
- Micrometer Naming: https://micrometer.io/docs/concepts#_naming_meters
- Spring Boot Actuator 3.5 access mode: `management.endpoint.<id>.access`

---

**Next Step**: `/pdca do phase2.1-metrics-fe-refactor` → Step 1 부터 순차 실행 (PDCA 자율 진행 정책 적용)
