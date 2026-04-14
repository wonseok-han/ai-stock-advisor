# Phase 2.1 — Metrics & FE Refactor Gap Analysis

> **Feature**: `phase2.1-metrics-fe-refactor`
> **Analyzed**: 2026-04-14
> **Branch**: `feat/phase2.1-metrics-fe-refactor` (PR #3)
> **Design SoR**: `docs/02-design/features/phase2.1-metrics-fe-refactor.design.md`

---

## 1. Executive Summary

| 항목 | 값 |
|---|---|
| 요구사항 매트릭스 Match Rate | **100%** (25 / 25) |
| Acceptance §9 가중 Match Rate | **100%** |
| 95% 목표 달성 여부 | **달성** ✅ |
| Major Gap | 0건 (해소) |
| Minor Gap | 0건 (해소) |
| Phase 2 잔여 Major gap (FR-15 + FE 폴더) | **완전 해소** ✅ |

**pdca-iterator 1회 iterate 로 3건 Gap 전부 해소. 25/25 100% 달성.**
- G-3 (application.yml prometheus.access) 해소
- G-2 (ActuatorExposureTest 2케이스) 해소
- G-1 (GeminiLlmClientMetricsTest T-1~T-4+T-8 6케이스) 해소
- 추가: LlmMetricsBinder 신설, GlobalExceptionHandler NoResourceFoundException 404 처리
- 전체 테스트: **33/33 pass** (RedTeam 20/20 유지, 신규 8케이스 추가)
- Commit: `57aba61`

---

## 2. 검증 범위

### 입력 문서
- Plan: `docs/01-plan/features/phase2.1-metrics-fe-refactor.plan.md`
- Design: `docs/02-design/features/phase2.1-metrics-fe-refactor.design.md`

### 검증 대상 구현
- BE: `apps/api/src/main/**` + `apps/api/src/test/**`
- FE: `apps/web/src/features/**`
- Infra: `apps/api/build.gradle.kts`, `apps/api/src/main/resources/application.yml`, `.github/workflows/forbidden-terms.yml`
- Commits: `e9fd9e6` (docs + FE rename) → `60157a5` (BE Micrometer) → `1762d22` (FE import 교정) → `e8cfbd5` (CI 스캔 범위 조정)

---

## 3. 요구사항 매트릭스 (25 항목)

### 3.1 BE — Micrometer Metric Schema (Design §3)

| # | 요구사항 | 상태 | 증거 |
|---|---|:---:|---|
| 1 | `llm.call.count` Counter (feature, model tag) | ✅ | `GeminiLlmClient.java` counter 주입 |
| 2 | `llm.token.total` Counter (direction, model tag) | ✅ | `GeminiLlmClient#recordTokens` |
| 3 | `llm.failure.count` — reason=timeout | ✅ | `GeminiLlmClient` WebClient timeout 경로 |
| 4 | `llm.failure.count` — reason=http | ✅ | `GeminiLlmClient` WebClientResponseException 분기 |
| 5 | `llm.failure.count` — reason=parse | ✅ | `GeminiLlmClient` JSON 파싱 실패 분기 |
| 6 | `llm.failure.count` — reason=validation | ✅ | `ResponseValidator#recordValidationFailure` |
| 7 | `llm.failure.count` — reason=forbidden | ✅ | `ResponseValidator#recordForbiddenHit` |
| 8 | `llm.forbidden.hit.count` — layer=validator | ✅ | `ResponseValidator` 금지용어 경로 |
| 9 | `llm.forbidden.hit.count` — layer=filter | ✅ | `LegalGuardFilter#sanitize` 치환 경로 |
| 10 | `llm.call.latency` Timer (feature, outcome tag) | ✅ | `GeminiLlmClient#recordLatency` |
| 11 | Tag cardinality allowlist (ticker/userId 배제) | ✅ | 전수 확인, enum 상수만 태그로 노출 |
| 12 | `LlmMetrics` 상수 클래스 | ✅ | `common/metrics/LlmMetrics.java` |

### 3.2 BE — Infra / Actuator (Design §4.6, §5)

| # | 요구사항 | 상태 | 증거 |
|---|---|:---:|---|
| 13 | `micrometer-registry-prometheus` 의존성 | ✅ | `apps/api/build.gradle.kts` |
| 14 | `management.endpoints.exposure.include=health,info,metrics,prometheus` | ✅ | `application.yml` |
| 15 | `management.metrics.tags.application=ai-stock-advisor` | ✅ | `application.yml` |
| 16 | `management.endpoint.prometheus.access=read_only` | ✅ | `application.yml` 추가 (commit 57aba61) |
| 17 | `LlmClient#generate(..., feature)` default 오버로드 | ✅ | `LlmClient.java` backward-compat |
| 18 | `AiSignalService` feature 전달 | ✅ | `FEATURE_AI_SIGNAL` 전달 |
| 19 | `NewsTranslator` feature 전달 | ✅ | `FEATURE_NEWS` 전달 |

### 3.3 BE — Tests (Design §5.1, §5.2)

| # | 요구사항 | 상태 | 증거 |
|---|---|:---:|---|
| 20 | `MicrometerMetricsTest` T-5/T-6/T-7 | ✅ | 4 케이스 green |
| 21 | `MicrometerMetricsTest` T-1/T-2/T-3/T-4/T-8 (Gemini 경로) | ✅ | `GeminiLlmClientMetricsTest.java` 6케이스 green (commit 57aba61) |
| 22 | `ActuatorExposureTest` 2 케이스 (prometheus 200, env 404) | ✅ | `ActuatorExposureTest.java` 2케이스 green (commit 57aba61) |

### 3.4 FE — 폴더 재배치 (Design §6)

| # | 요구사항 | 상태 | 증거 |
|---|---|:---:|---|
| 23 | `features/stock-detail/news/`, `features/stock-detail/ai-signal/` 존재 | ✅ | 4 파일 이동 완료 |
| 24 | 구 폴더 `features/news/`, `features/ai-signal/` 부재 | ✅ | glob 0 hit |
| 25 | `@/features/(news\|ai-signal)/` import 잔존 0건 | ✅ | grep 0 hit, 새 경로만 참조 |

---

## 4. Match Rate 산출

### 4.1 요구사항 매트릭스
- 전체: **25 항목**
- 달성: **25 항목**
- **Match Rate = 100%**

### 4.2 Acceptance §9 가중 기준
Design §9 Acceptance Criteria 8 항목 중 평가:

| # | Acceptance | 평가 |
|---|---|:---:|
| AC-1 | `/actuator/prometheus` 200 + `llm_*` 시계열 포함 | ✅ LlmMetricsBinder + ActuatorExposureTest A-1 green |
| AC-2 | `MicrometerMetricsTest` 8 케이스 pass (T-1~T-8 범위) | ✅ GeminiLlmClientMetricsTest 6케이스 + MicrometerMetricsTest 4케이스 = 10/10 |
| AC-3 | `ActuatorExposureTest` 2 케이스 pass | ✅ A-1 + A-2 green |
| AC-4 | 구 FE 폴더 부재 | ✅ |
| AC-5 | 신 FE 폴더 존재 | ✅ |
| AC-6 | `@/features/(news\|ai-signal)` grep 0 hit | ✅ |
| AC-7 | `./gradlew check` + RedTeam 20/20 | ✅ 33/33 pass |
| AC-8 | Match Rate ≥ 95% | ✅ 100% |

**가중 Match Rate = 100%** (8/8 완전 달성).

---

## 5. Gap 목록

### Major (0건) — 전부 해소

#### G-1. `GeminiLlmClient` Gemini 호출 경로 자동 테스트 (해소 ✅)
- **해소 커밋**: `57aba61`
- **증거**: `apps/api/src/test/java/com/aistockadvisor/ai/infra/GeminiLlmClientMetricsTest.java` — MockWebServer 기반 T-1~T-4+T-8 6케이스 green
- `build.gradle.kts` 에 `okhttp3:mockwebserver:4.12.0` 추가

### Minor (0건) — 전부 해소

#### G-2. `ActuatorExposureTest` (해소 ✅)
- **해소 커밋**: `57aba61`
- **증거**: `apps/api/src/test/java/com/aistockadvisor/ActuatorExposureTest.java` — A-1(prometheus 200) + A-2(env 404) 2케이스 green
- `GlobalExceptionHandler` 에 `NoResourceFoundException → 404` 핸들러 추가로 A-2 통과

#### G-3. `management.endpoint.prometheus.access=read_only` (해소 ✅)
- **해소 커밋**: `57aba61`
- **증거**: `apps/api/src/main/resources/application.yml:45` — `prometheus.access: read_only` 추가

---

## 6. 판정

| 기준 | 결과 |
|---|:---:|
| Phase 2 잔여 Major gap 2건 해소 | ✅ **달성** (FR-15 Micrometer + FE 폴더 완전 해소) |
| Phase 2.1 자체 Match Rate ≥ 95% | ✅ **달성** (100%) |
| Major Gap | 0건 |
| Minor Gap | 0건 |

---

## 7. 권장 다음 단계

- `/pdca report phase2.1-metrics-fe-refactor` → 완료 리포트 작성
- PR #3 squash merge
- `/pdca archive phase2.1-metrics-fe-refactor`

---

## 8. 변경 요약 (PR #3)

| Commit | 파일 수 | 변경 |
|---|:---:|---|
| `e9fd9e6` docs(phase2.1) | 6 | Plan + Design 문서, FE 4파일 git mv |
| `60157a5` feat(api) | 11 | Micrometer 4 Counter + 1 Timer, LlmMetrics, application.yml, 테스트 |
| `1762d22` refactor(web) | 3 | FE import path 교정 3건 |
| `e8cfbd5` ci | 1 | forbidden-terms scan 범위를 production 소스로 한정 |
| `57aba61` fix(api) G-1/G-2/G-3 | 6 | LlmMetricsBinder, GeminiLlmClientMetricsTest, ActuatorExposureTest, application.yml, GlobalExceptionHandler, build.gradle.kts |
| **합계** | **27** | **+874 / -18 + 반나절 iterate 분** |

CI 상태: **4 / 4 green** (Forbidden terms, Web build, API check, GitGuardian) + 로컬 `./gradlew check` 33/33

---

**작성**: gap-detector agent (via bkit:pdca analyze)
**문서 버전**: 1.0
**관련 PR**: https://github.com/wonseok-han/ai-stock-advisor/pull/3
