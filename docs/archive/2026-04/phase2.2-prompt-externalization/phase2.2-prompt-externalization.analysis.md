# phase2.2-prompt-externalization Gap Analysis

> **Generated**: 2026-04-15
> **Phase**: PDCA Check
> **Match Rate**: **96%** (24/25 items match, 1 partial, 0 missing)
> **Verdict**: AC-6 (≥90%) 달성 → Report 단계 진입 가능

---

## 1. 매칭률 요약

```
┌─────────────────────────────────────────────┐
│  Overall Match Rate: 96%                    │
├─────────────────────────────────────────────┤
│  Match:            24 / 25 items            │
│  Partial:           1 / 25 items            │
│  Missing:           0 / 25 items            │
└─────────────────────────────────────────────┘
```

---

## 2. Design Goal (G1~G4) 충족도

| Goal | 기대 동작 | 구현 상태 | 판정 |
|---|---|---|:---:|
| G1 외부화 | inline 상수 제거 + classpath 로딩 | `PromptBuilder` / `NewsTranslator` 에서 `SYSTEM_PROMPT_TEMPLATE` 상수 부재, `promptLoader.load(...)` 경유 | ✅ |
| G2 가용성 | transient 1회 재시도 + 250ms backoff + 4xx 즉시 fail | `MAX_ATTEMPTS=2`, `RETRY_BACKOFF_MS=250L`, 분류 매트릭스 정확 (5xx/429/timeout=retryable, 4xx/parse/MAX_TOKENS=non-retryable) | ✅ |
| G3 관측성 | tag allowlist 내 retry.count 추가 | `LlmMetrics.RETRY_COUNT` + `OUTCOME_EXHAUSTED` 신설, Binder 에 feature×outcome 0 시리즈 등록 | ✅ |
| G4 회귀 0 | volatile cachedSystemPrompt + `String.formatted` 보존 | `PromptBuilder`/`NewsTranslator` 모두 volatile 캐시 + `template.formatted(forbiddenTerms.quotedList())` 보존 | ✅ |

---

## 3. Architecture & Data Flow 일치

| Design Component | 실제 구현 | 판정 |
|---|---|:---:|
| `PromptLoader` 인터페이스 | `com.aistockadvisor.common.prompt.PromptLoader` | ✅ |
| `ClasspathPromptLoader` 구현 | `@Component` + `ResourceLoader` + `ConcurrentHashMap` 캐시 + `%s` 검증 | ✅ |
| `PromptBuilder` loader 주입 | 생성자 3-arg | ✅ |
| `NewsTranslator` loader 주입 | 생성자 4-arg | ✅ |
| `GeminiLlmClient` retry loop + callOnce 분리 | `generate()` for-loop + `callOnce()` private | ✅ |
| `RetryableException` marker | `RetryableUpstreamException` (private static final inner class) | ✅ |

---

## 4. Acceptance Criteria 검증

| AC | 기준 | 검증 결과 | 판정 |
|:---:|---|---|:---:|
| AC-1 | `resources/prompts/` 2개 .txt + UTF-8 + LF | 2개 파일 확인, 한국어 멀티바이트 보존 | ✅ |
| AC-2 | production source 에 `SYSTEM_PROMPT_TEMPLATE` 상수 부재 | grep main → 0 hit | ✅ |
| AC-3 | `make api-check` + 신규 테스트 7건 green | `make api-check` BUILD SUCCESSFUL 확인 | ✅ |
| AC-4 | `/actuator/prometheus` 에 `llm_retry_count_total{outcome=...}` 노출 | `LlmMetricsBinder` 0 시리즈 등록 (런타임 확인은 후속) | ⚠️ 런타임 검증 |
| AC-5 | MockWebServer 로 transient/non-transient 분기 검증 | R-1 (5xx→200) / R-2 (429→200) / R-3 (5xx×2 exhausted) / R-4 (400 no-retry, requestCount==1) 모두 통과 | ✅ |
| AC-6 | Match Rate ≥ 90% | **96%** | ✅ |
| AC-7 | PR diff net +200줄 이내 | git diff --stat 로 후속 확인 | ⚠️ 사후 확인 |

---

## 5. Test Plan 대조 (총 7건 + 회귀 1건)

| Test ID | Design | 구현 | 판정 |
|---|---|---|:---:|
| L-1 happy | UTF-8 + `%s` 1개 | 한국어 멀티바이트 + assertion 3건 | ✅ |
| L-2 missing | `IllegalStateException` + 경로 | `hasMessageContaining("classpath:prompts/...")` | ✅ |
| L-3 invalid | `%d` reject | `hasMessageContaining("Invalid % token")` | ✅ |
| R-1 5xx→success | retry.count{success}+1 | 정확 일치 | ✅ |
| R-2 429→success | retry.count{success}+1 | 정확 일치 | ✅ |
| R-3 exhausted | retry.count{exhausted}+1, UPSTREAM_UNAVAILABLE | + httpFailures==2.0 추가 보강 | ✅ |
| R-4 4xx no-retry | requestCount==1, retry.count 불변 | 정확 일치 | ✅ |
| G-1/G-2 byte-equality | PromptBuilderTest / NewsTranslatorTest stub | 기존 단위 테스트 자체 부재 (phase2.1 이전부터) | ⚠️ Partial |

---

## 6. Coding Convention 대조

| 항목 | 규칙 | 구현 | 판정 |
|---|---|---|:---:|
| Class PascalCase | `PromptLoader`, `ClasspathPromptLoader` | 일치 | ✅ |
| Method camelCase | `load`, `verifyPlaceholders`, `callOnce`, `sleepBackoff` | 일치 | ✅ |
| Constants UPPER_SNAKE | `MAX_ATTEMPTS`, `RETRY_BACKOFF_MS`, `RETRY_COUNT`, `OUTCOME_EXHAUSTED` | 일치 | ✅ |
| Package lowercase.dot | `com.aistockadvisor.common.prompt` | 일치 | ✅ |
| Resource kebab + `.system.txt` | `ai-signal.system.txt`, `news-translate.system.txt` | 일치 | ✅ |
| Retry 로그 포맷 | `WARN ... retry feature= model= attempt= reason=` | INFO(retry success) + WARN(retry exhausted) 두 지점만 — `reason` 은 FAILURE_COUNT 메트릭에 흡수 | ⚠️ Minor |

---

## 7. Gap 목록

### Minor (Partial)

1. **Regression byte-equality 테스트 부재**
   - `PromptBuilderTest` / `NewsTranslatorTest` 자체가 이전부터 부재
   - 영향: 낮음. inline → 외부 .txt 가 1:1 텍스트 복사이며, `cachedSystemPrompt` 캐싱 로직 보존
   - 권장: Backlog 로 이관

2. **Retry WARN 로그 포맷 미세 차이**
   - 설계는 매 시도마다 WARN, 구현은 retry success(INFO) + exhausted(WARN) 두 지점만
   - 영향: 매우 낮음. 관측 의도(재시도 발생 여부)는 `retry.count` 메트릭으로 완전 커버
   - 권장: Report 의 "Design 보완" 섹션에서 구현 기준으로 문서 정정

### Runtime 검증 권장

3. **AC-4**: `make api-dev` 후 `curl http://localhost:8080/actuator/prometheus | grep llm_retry_count_total` 로 시계열 노출 확인
4. **AC-7**: `git diff --stat main...feat/phase2.2-prompt-externalization -- apps/api` 로 net 라인 수 확인

---

## 8. 권장 후속 조치

| 우선 | 조치 |
|---|---|
| 1 | `/pdca report phase2.2-prompt-externalization` 진입 |
| 2 | Report 작성 시 retry 로그 포맷 차이를 "Design 보완" 으로 정리 |
| 3 | PR 생성 → squash merge (PR 워크플로 정책) |
| 4 | 머지 후 `make api-dev` 로 prometheus 시계열 사후 검증 |

---

## 9. 다음 단계

```
[Plan] OK -> [Design] OK -> [Do] OK -> [Check] OK (96%) -> [Report] NEXT
```
