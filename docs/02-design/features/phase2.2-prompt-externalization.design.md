# phase2.2-prompt-externalization Design Document

> **Summary**: `PromptBuilder` / `NewsTranslator` 의 시스템 프롬프트를 `classpath:prompts/*.txt` 로 외부화하고, 신규 `PromptLoader` 컴포넌트가 UTF-8 + 단일 `%s` placeholder 검증을 보장. `GeminiLlmClient.generate()` 에 transient 한정 1회 재시도(고정 250ms) + `llm.retry.count` Counter 를 추가.
>
> **Project**: AI Stock Advisor
> **Version**: Phase 2.2 (Phase 2 deferred 정리)
> **Author**: wonseok-han + Claude
> **Date**: 2026-04-15
> **Status**: Draft
> **Planning Doc**: [phase2.2-prompt-externalization.plan.md](../../01-plan/features/phase2.2-prompt-externalization.plan.md)

---

## 1. Overview

### 1.1 Design Goals

- **G1 (외부화):** 프롬프트 텍스트를 Java 컴파일 단위에서 분리해 운영 PR 의 비용을 한 줄 수정 ↔ 한 번 빌드/배포로 동일하게 유지하되, 변경 단위를 작게 만든다.
- **G2 (가용성):** Gemini API 의 일시적 5xx/429/IO 장애가 사용자 fallback 응답으로 직결되지 않도록 1회만 재시도한다. 절대 다회 재시도하지 않는다 (quota 폭주 방지).
- **G3 (관측성 일관):** Phase 2.1 에서 합의한 메트릭 tag allowlist (feature/model/direction/reason/layer/outcome) 를 깨지 않는 범위에서만 추가한다.
- **G4 (회귀 0):** 기존 33+ 단위 테스트와 inline 프롬프트의 출력 동등성을 보장한다 (캐시된 system prompt 문자열 byte-for-byte 동일).

### 1.2 Design Principles

- **단일 책임 분리** — 텍스트 로딩(`PromptLoader`)과 템플릿 조립(`PromptBuilder` / `NewsTranslator`) 분리
- **Fail-fast** — resource 누락·placeholder 위반은 기동 시점이나 첫 호출에 명확한 예외로 차단 (silent fallback 금지, Phase 2.1 SSOT 원칙 유지)
- **YAGNI** — Resilience4j / hot-reload / 템플릿 엔진 도입 안 함, 필요 시 다음 사이클
- **이미 검증된 패턴 보존** — `volatile cachedSystemPrompt`, `String.formatted("%s", banned)` 그대로

---

## 2. Architecture

### 2.1 Component Diagram

```
┌──────────────────┐         ┌──────────────────┐
│  PromptBuilder   │────┐    │  NewsTranslator  │────┐
│  (ai-signal)     │    │    │  (news)          │    │
└──────────────────┘    │    └──────────────────┘    │
                        ▼                            ▼
              ┌────────────────────────────────────────┐
              │            PromptLoader (NEW)          │
              │  load(name) → String  + cache + verify │
              └────────────┬───────────────────────────┘
                           │
                           ▼
              classpath:prompts/
                ├── ai-signal.system.txt
                └── news-translate.system.txt

┌──────────────────┐
│  GeminiLlmClient │  generate() → tryOnce() ──┐
│  + retry loop    │                            │ transient?
└────────┬─────────┘                            ▼
         │                                  sleep 250ms
         ▼                                      │
   Gemini API ◀──────── retry once ─────────────┘
         │
         ▼
   llm.retry.count{feature, model, outcome=success|exhausted}
```

### 2.2 Data Flow — Retry 시퀀스

```
caller                  GeminiLlmClient                Gemini API
  │  generate(sys, usr) ─▶ │
  │                        │ tryOnce(attempt=1) ──────▶ │
  │                        │                       (5xx/429/IO)
  │                        │ ◀────── failure ─────────── │
  │                        │ classify(ex) → transient
  │                        │ Thread.sleep(250)
  │                        │ tryOnce(attempt=2) ──────▶ │
  │                        │ ◀──── 200 OK ─────────────│
  │                        │ retry.count{outcome=success}+1
  │ ◀── LlmResult ─────────│
```

비-transient 경로:

```
  │ generate() ─▶ │ tryOnce(1) ─▶ │
  │               │ ◀── 400 / parse error / empty body
  │               │ classify → non-transient
  │               │ throw immediately (no retry, no counter inc)
```

### 2.3 Dependencies

| Component | Depends On | Purpose |
|-----------|-----------|---------|
| `PromptLoader` (신규) | `org.springframework.core.io.ResourceLoader` | classpath UTF-8 텍스트 읽기 |
| `PromptBuilder` | `PromptLoader`, `ForbiddenTermsRegistry` | system prompt 빌드 + 캐싱 |
| `NewsTranslator` | `PromptLoader`, `ForbiddenTermsRegistry` | news system prompt 빌드 + 캐싱 |
| `GeminiLlmClient` | `MeterRegistry` (기존), `LlmMetrics` (RETRY_COUNT 상수 신설) | 재시도 + 메트릭 |
| (변경 없음) | `LlmMetricsBinder` | RETRY_COUNT 초기 0 시리즈 등록만 추가 |

---

## 3. 핵심 설계: PromptLoader

### 3.1 인터페이스

```java
package com.aistockadvisor.common.prompt;

/**
 * classpath:prompts/{name} UTF-8 텍스트 로더.
 *
 * <p>로드 시 1회 placeholder 검증:
 * {@code %s} 가 정확히 0개 또는 1개여야 한다. 그 외 % 는 모두 {@code %%} 로 이스케이프되어야 함.
 *
 * <p>스레드 안전. 결과는 인스턴스 단위로 캐시 (key=name).
 */
public interface PromptLoader {
    /** classpath:prompts/{name} 텍스트 반환. 누락/IO 실패 시 IllegalStateException. */
    String load(String name);
}
```

### 3.2 구현 (스케치)

```java
@Component
public class ClasspathPromptLoader implements PromptLoader {

    private static final String BASE = "prompts/";
    private final ResourceLoader resourceLoader;
    private final ConcurrentMap<String, String> cache = new ConcurrentHashMap<>();

    public ClasspathPromptLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public String load(String name) {
        return cache.computeIfAbsent(name, this::loadAndVerify);
    }

    private String loadAndVerify(String name) {
        Resource res = resourceLoader.getResource("classpath:" + BASE + name);
        if (!res.exists()) {
            throw new IllegalStateException(
                    "Prompt resource not found: classpath:" + BASE + name);
        }
        try (InputStream in = res.getInputStream()) {
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            verifyPlaceholders(name, text);
            return text;
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Failed to read prompt: classpath:" + BASE + name, ex);
        }
    }

    /** %s 외의 % 는 모두 %% 로 이스케이프되어야 함 (String.formatted 안전). */
    private void verifyPlaceholders(String name, String text) {
        // 단순 카운팅: %% 제거 후 % 위치를 훑어 %s 1개 이하만 허용.
        String stripped = text.replace("%%", "");
        int idx = 0, count = 0;
        while ((idx = stripped.indexOf('%', idx)) != -1) {
            if (idx + 1 >= stripped.length() || stripped.charAt(idx + 1) != 's') {
                throw new IllegalStateException(
                        "Invalid % token in prompt " + name + " at index " + idx);
            }
            count++;
            idx += 2;
        }
        if (count > 1) {
            throw new IllegalStateException(
                    "Prompt " + name + " contains more than one %s placeholder");
        }
    }
}
```

### 3.3 PromptBuilder / NewsTranslator 호출 변경

```java
// PromptBuilder
public PromptBuilder(ObjectMapper objectMapper,
                     ForbiddenTermsRegistry forbiddenTerms,
                     PromptLoader promptLoader) {
    this.objectMapper = objectMapper;
    this.forbiddenTerms = forbiddenTerms;
    this.promptLoader = promptLoader;
}

public String systemPrompt() {
    String cached = cachedSystemPrompt;
    if (cached != null) return cached;
    String template = promptLoader.load("ai-signal.system.txt");
    String built = template.formatted(forbiddenTerms.quotedList());
    this.cachedSystemPrompt = built;
    return built;
}
// SYSTEM_PROMPT_TEMPLATE 상수 → 삭제
```

`NewsTranslator` 도 동일 패턴 (`news-translate.system.txt`).

---

## 4. 핵심 설계: Gemini Retry Loop

### 4.1 분류 매트릭스 — 어떤 실패가 transient 인가

| 실패 유형 | 예외/조건 | Transient? | Retry? |
|---|---|:---:|:---:|
| HTTP 5xx | `WebClientResponseException` + `status.is5xxServerError()` | ✅ | ✅ |
| HTTP 429 | `WebClientResponseException` + `TOO_MANY_REQUESTS` | ✅ | ✅ |
| HTTP 4xx (≠429) | `WebClientResponseException` + 4xx | ❌ | ❌ |
| Connect/Read timeout | `RuntimeException` (reactor.netty `ReadTimeoutException`) | ✅ | ✅ |
| IOException (connection reset 등) | `RuntimeException` 래핑된 `IOException` | ✅ | ✅ |
| no-candidates / empty body | 정상 200 + body 검증 실패 | ❌ | ❌ |
| MAX_TOKENS finishReason | 정상 200 + truncation | ❌ | ❌ |
| API key 미설정 | `BusinessException` (사전 검사) | ❌ | ❌ |

> **재시도 안 하는 이유 (4xx/parse/MAX_TOKENS):** 동일 요청을 다시 보내도 결과가 같다. 재시도는 *서버 일시 장애* 한정.

### 4.2 구현 (스케치)

```java
private static final int MAX_ATTEMPTS = 2;            // 최초 1 + 재시도 1
private static final long RETRY_BACKOFF_MS = 250L;

@Override
public LlmResult generate(String systemPrompt, String userPrompt, String feature) {
    String model = props.modelOrDefault();
    incrementCallCount(feature, model);
    if (props.apiKey() == null || props.apiKey().isBlank()) {
        throw new BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE,
                "Gemini API key is not configured.", null);
    }

    long start = System.currentTimeMillis();
    Throwable lastTransient = null;
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
        try {
            return callOnce(systemPrompt, userPrompt, feature, model, start);
        } catch (RetryableException ex) {           // 내부 전용 marker
            lastTransient = ex.getCause();
            if (attempt == MAX_ATTEMPTS) {
                meterRegistry.counter(LlmMetrics.RETRY_COUNT,
                        LlmMetrics.TAG_FEATURE, feature,
                        LlmMetrics.TAG_MODEL, model,
                        LlmMetrics.TAG_OUTCOME, LlmMetrics.OUTCOME_EXHAUSTED
                ).increment();
                throw ex.toBusinessException();
            }
            log.warn("gemini call retry feature={} model={} attempt={} reason={}",
                    feature, model, attempt + 1, ex.reason());
            sleepQuiet(RETRY_BACKOFF_MS);
        }
    }
    throw new IllegalStateException("unreachable", lastTransient);
}

/** callOnce 안에서 transient 분류된 케이스만 RetryableException 으로 다시 던진다. */
private LlmResult callOnce(String sys, String usr, String feature, String model, long start) {
    try {
        // ... 기존 webClient 호출 + body 검증 ... (변경 없음)
    } catch (WebClientResponseException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        recordFailure(feature, LlmMetrics.REASON_HTTP, start);
        if (status == HttpStatus.TOO_MANY_REQUESTS) {
            throw new RetryableException("rate_limit", ex,
                    () -> new BusinessException(ErrorCode.UPSTREAM_RATE_LIMIT, null, null, ex));
        }
        if (status != null && status.is5xxServerError()) {
            throw new RetryableException("upstream_5xx", ex,
                    () -> new BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE, null, null, ex));
        }
        throw new BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE, null, null, ex);  // 4xx 즉시 throw
    } catch (BusinessException ex) {
        throw ex;                                    // body 검증 실패 등 — non-transient
    } catch (RuntimeException ex) {
        recordFailure(feature, LlmMetrics.REASON_TIMEOUT, start);
        throw new RetryableException("timeout_io", ex,
                () -> new BusinessException(ErrorCode.UPSTREAM_TIMEOUT, null, null, ex));
    }
}

/** retry 성공 시: 두 번째 callOnce 가 정상 반환되므로 별도 처리 없이 카운터만 후처리. */
// 위 for-loop 안에서, 첫 attempt 실패 → 두 번째 attempt 성공한 경우
// callOnce 정상 반환 직전에 retry success counter 를 증가.
```

> **간소화 옵션:** `Mono.retryWhen(Retry.fixedDelay(1, Duration.ofMillis(250)).filter(this::isTransient))` 로도 동일 동작 가능. 구현 단계에서 둘 중 더 짧은 쪽 채택. 본 설계는 **분류 매트릭스 자체가 SoR** 이며 구현은 동등하면 무방하다.

### 4.3 메트릭 추가

`LlmMetrics.java` 에 상수 추가:

```java
public static final String RETRY_COUNT = "llm.retry.count";
public static final String OUTCOME_EXHAUSTED = "exhausted";   // 신규
// OUTCOME_SUCCESS 는 기존 사용
```

`LlmMetricsBinder` 에 초기 0 시리즈 등록:

```java
// retry.count — feature × outcome (모델은 단일이라 별도 루프 불요)
for (String outcome : new String[]{LlmMetrics.OUTCOME_SUCCESS, LlmMetrics.OUTCOME_EXHAUSTED}) {
    registry.counter(LlmMetrics.RETRY_COUNT,
            LlmMetrics.TAG_FEATURE, LlmMetrics.FEATURE_AI_SIGNAL,
            LlmMetrics.TAG_MODEL, model,
            LlmMetrics.TAG_OUTCOME, outcome);
}
```

> **태그 cardinality:** feature(2) × model(1) × outcome(2) = 4 시계열. Phase 2.1 의 22 시계열에 +4 → 총 26.

---

## 5. 파일 변경 목록

| 변경 | 파일 | 액션 |
|---|---|---|
| 신설 | `apps/api/src/main/resources/prompts/ai-signal.system.txt` | `PromptBuilder.SYSTEM_PROMPT_TEMPLATE` 1:1 이동 (line 24-47) |
| 신설 | `apps/api/src/main/resources/prompts/news-translate.system.txt` | `NewsTranslator.SYSTEM_PROMPT_TEMPLATE` 1:1 이동 (line 29-43) |
| 신설 | `apps/api/src/main/java/com/aistockadvisor/common/prompt/PromptLoader.java` | 인터페이스 |
| 신설 | `apps/api/src/main/java/com/aistockadvisor/common/prompt/ClasspathPromptLoader.java` | 구현 + placeholder 검증 |
| 수정 | `apps/api/src/main/java/com/aistockadvisor/ai/service/PromptBuilder.java` | 상수 제거, 생성자에 `PromptLoader` 추가, `systemPrompt()` 가 loader.load() 호출 |
| 수정 | `apps/api/src/main/java/com/aistockadvisor/news/service/NewsTranslator.java` | 동일 패턴 |
| 수정 | `apps/api/src/main/java/com/aistockadvisor/ai/infra/GeminiLlmClient.java` | retry 루프, callOnce 분리, RETRY_COUNT 카운터 |
| 수정 | `apps/api/src/main/java/com/aistockadvisor/common/metrics/LlmMetrics.java` | `RETRY_COUNT`, `OUTCOME_EXHAUSTED` 상수 추가 |
| 수정 | `apps/api/src/main/java/com/aistockadvisor/common/metrics/LlmMetricsBinder.java` | retry.count 초기 0 시리즈 등록 |
| 신설 | `apps/api/src/test/.../common/prompt/ClasspathPromptLoaderTest.java` | 3 cases |
| 신설 | `apps/api/src/test/.../ai/infra/GeminiLlmClientRetryTest.java` | 4 cases |
| 수정 | (필요 시) `PromptBuilderTest.java`, `NewsTranslatorTest.java` | 생성자 시그니처 변경 반영 |

---

## 6. Error Handling

| 상황 | 동작 | 예외 / Status |
|---|---|---|
| `prompts/ai-signal.system.txt` 누락 | 첫 호출 시 즉시 fail-fast | `IllegalStateException("Prompt resource not found: ...")` → Spring 이 BeanCreation/RuntimeException → 5xx |
| `prompts/*.txt` 안에 `%d` 등 잘못된 토큰 | 첫 호출 시 fail-fast | `IllegalStateException("Invalid % token ...")` |
| `prompts/*.txt` 에 `%s` 가 2개 이상 | 첫 호출 시 fail-fast | `IllegalStateException("more than one %s placeholder")` |
| Gemini 5xx (1회째) | 250ms sleep 후 1회 재시도 | `WARN gemini call retry attempt=2 reason=upstream_5xx` |
| Gemini 5xx (2회 연속) | retry exhausted, `BusinessException(UPSTREAM_UNAVAILABLE)` throw | `llm_retry_count_total{outcome="exhausted"} +1` |
| Gemini 5xx → 200 (재시도 성공) | 정상 응답 반환 | `llm_retry_count_total{outcome="success"} +1` |
| Gemini 4xx (400 INVALID_ARGUMENT) | 즉시 fail (재시도 안 함) | `BusinessException(UPSTREAM_UNAVAILABLE)` |
| 응답 body 검증 실패 (no candidates / empty / MAX_TOKENS) | 즉시 fail | `BusinessException(LLM_VALIDATION_FAILED)`, `failure.count{reason=parse}` |

---

## 7. Security Considerations

- [x] Forbidden 용어 SoR 변경 없음 (`legal/forbidden-terms.json` 그대로). 외부화된 `.txt` 안에 forbidden 용어 직접 등장 금지 — `%s` placeholder 로 런타임 주입.
- [x] 프롬프트 boundary 마커(`<<<CONTEXT_BEGIN>>>`) 보존 (Guard Level 2 유지)
- [x] CI grep 스캔: production source 범위에서 `SYSTEM_PROMPT_TEMPLATE` 상수 부재 검증 (Phase 2.1 패턴 재사용)
- [x] 재시도가 4xx 를 retry 하지 않으므로 brute-force / 의도된 reject 우회 불가

---

## 8. Test Plan

### 8.1 Test Scope

| Type | Target | Tool |
|------|--------|------|
| Unit | `ClasspathPromptLoader` 로드/검증 | JUnit 5 + Mockito (필요 시 Spring `@SpringBootTest` 없이 `DefaultResourceLoader`) |
| Unit | `GeminiLlmClient` 재시도 분기 | JUnit 5 + okhttp `MockWebServer` (Phase 2.1 패턴 재사용) |
| Regression | `PromptBuilderTest`, `NewsTranslatorTest` | 기존 시그니처 + PromptLoader stub |
| Smoke | `make api-check` + `/actuator/prometheus` 수동 확인 | Gradle + curl |

### 8.2 Test Cases (Key)

#### ClasspathPromptLoaderTest

- **L-1 happy:** 실제 `prompts/ai-signal.system.txt` (테스트 픽스처) 로드 → 내용 일치 + UTF-8 멀티바이트(`한국어`) 보존
- **L-2 missing:** 존재하지 않는 파일명 → `IllegalStateException` + 메시지에 경로 포함
- **L-3 invalid placeholder:** 픽스처에 `%d` 포함 → `IllegalStateException("Invalid % token")`

> `%s` 가 2개인 케이스는 placeholder 검증 코드 brunch 로만 충분 (별도 케이스 필요 시 L-3a 추가)

#### GeminiLlmClientRetryTest (MockWebServer)

- **R-1 transient 5xx → success:** 1회 503, 2회 200 → `LlmResult` 반환, `retry.count{outcome=success}+1`, `WARN attempt=2 reason=upstream_5xx` 1건
- **R-2 transient 429 → success:** 1회 429, 2회 200 → 동일 패턴
- **R-3 exhausted (5xx 2회):** 1회 503, 2회 503 → `BusinessException(UPSTREAM_UNAVAILABLE)`, `retry.count{outcome=exhausted}+1`
- **R-4 non-transient 4xx (no retry):** 1회 400 → 즉시 throw, MockWebServer 호출 횟수 = 1, `retry.count` 변화 없음

#### Regression

- **G-1 PromptBuilder system prompt byte-equality:** PromptLoader stub 가 기존 inline 텍스트를 반환 → `systemPrompt()` 출력이 phase2.1 산출물과 byte-for-byte 동일
- **G-2 NewsTranslator** 동일

---

## 9. Acceptance Criteria

- [ ] **AC-1** `apps/api/src/main/resources/prompts/` 에 2개 .txt 존재 + UTF-8 + LF
- [ ] **AC-2** `PromptBuilder.SYSTEM_PROMPT_TEMPLATE` / `NewsTranslator.SYSTEM_PROMPT_TEMPLATE` Java 상수 부재 (CI grep 검증)
- [ ] **AC-3** `make api-check` 통과 + 신규 테스트 7건 모두 green
- [ ] **AC-4** `/actuator/prometheus` 응답에 `llm_retry_count_total{outcome="success"}` / `{outcome="exhausted"}` 시계열 노출
- [ ] **AC-5** MockWebServer 검증으로 transient 5xx/429 1회는 자동 복구, 4xx 는 즉시 실패
- [ ] **AC-6** gap-detector Match Rate ≥ 90%
- [ ] **AC-7** PR diff 가 net +200줄 이내 (외부화 ─ 0 줄, 신설 + 재시도 + 테스트 합산)

---

## 10. Coding Convention Reference

### 10.1 BE 명명 (CLAUDE.md 준수)

| Target | Rule | 본 사이클 적용 예 |
|---|---|---|
| Class | PascalCase | `PromptLoader`, `ClasspathPromptLoader` |
| Method | camelCase | `load`, `verifyPlaceholders` |
| Constants | UPPER_SNAKE_CASE | `MAX_ATTEMPTS`, `RETRY_BACKOFF_MS` |
| Package | lowercase.dot | `com.aistockadvisor.common.prompt` |
| Resource | kebab-case + `.system.txt` | `ai-signal.system.txt`, `news-translate.system.txt` |

### 10.2 본 사이클 신설 컨벤션

| 항목 | 규칙 |
|---|---|
| Prompt 파일 위치 | `apps/api/src/main/resources/prompts/{feature}.{role}.txt` |
| Prompt 인코딩 | UTF-8, LF only, BOM 금지 |
| Prompt placeholder | 단일 `%s` 만 허용. 그 외 `%` 는 `%%` 로 이스케이프 |
| 재시도 로그 | `WARN gemini call retry feature={} model={} attempt={} reason={}` |
| Retry counter outcome | `success` / `exhausted` (Phase 2.1 OUTCOME 셋 확장) |

---

## 11. Implementation Guide

### 11.1 File Structure (변경 후)

```
apps/api/src/main/
├── java/com/aistockadvisor/
│   ├── ai/
│   │   ├── infra/GeminiLlmClient.java          # MOD: retry loop
│   │   └── service/PromptBuilder.java           # MOD: PromptLoader 주입
│   ├── news/service/NewsTranslator.java         # MOD: PromptLoader 주입
│   ├── common/
│   │   ├── prompt/                              # NEW
│   │   │   ├── PromptLoader.java
│   │   │   └── ClasspathPromptLoader.java
│   │   └── metrics/
│   │       ├── LlmMetrics.java                  # MOD: +RETRY_COUNT, +OUTCOME_EXHAUSTED
│   │       └── LlmMetricsBinder.java            # MOD: retry.count 0 시리즈
└── resources/prompts/                           # NEW
    ├── ai-signal.system.txt
    └── news-translate.system.txt
```

### 11.2 Implementation Order

1. [ ] **Step 1 — Loader 신설:** `PromptLoader` + `ClasspathPromptLoader` 작성, `ClasspathPromptLoaderTest` 3 cases (L-1/L-2/L-3) green
2. [ ] **Step 2 — 프롬프트 외부화:** `prompts/*.txt` 2개 신설, `PromptBuilder` / `NewsTranslator` inline 상수 제거 + loader 주입, regression test G-1/G-2 byte-equality 확인
3. [ ] **Step 3 — Retry 메트릭 상수:** `LlmMetrics.RETRY_COUNT` / `OUTCOME_EXHAUSTED` 추가, `LlmMetricsBinder` 0 시리즈 등록 (LlmMetricsBinderSmokeTest 회귀 확인)
4. [ ] **Step 4 — Retry 루프:** `GeminiLlmClient` `callOnce` 추출 + `for-loop` 도입, 분류 매트릭스 구현
5. [ ] **Step 5 — Retry 테스트:** `GeminiLlmClientRetryTest` R-1 ~ R-4 작성 + green
6. [ ] **Step 6 — 통합 검증:** `make api-check` → `make api-dev` → `/actuator/prometheus` 수동 확인 → `/pdca analyze`

> 각 Step 은 단일 commit 으로 분리 권장 (gap-detector 가 단계별 변경을 추적하기 쉬움). 모든 step 완료 후 squash merge.

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 0.1 | 2026-04-15 | 초기 Design 작성 (PromptLoader 신설 + Gemini retry 분류 매트릭스 + 7 tests) | wonseok-han + Claude |
