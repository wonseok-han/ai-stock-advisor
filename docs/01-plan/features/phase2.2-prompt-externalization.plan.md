# phase2.2-prompt-externalization Planning Document

> **Summary**: LLM 시스템 프롬프트를 Java text block 에서 `classpath:prompts/*.txt` 로 외부화하고 Gemini 호출에 transient 실패 1회 재시도 루프를 도입하여, 프롬프트 변경 시 컴파일·재배포를 제거하고 일시적 네트워크/5xx 에 대한 가용성을 끌어올린다.
>
> **Project**: AI Stock Advisor
> **Version**: Phase 2.2 (Phase 2 후속 잔여)
> **Author**: wonseok-han (with Claude)
> **Date**: 2026-04-15
> **Status**: Draft

---

## Executive Summary

| Perspective | Content |
|-------------|---------|
| **Problem** | 프롬프트가 `PromptBuilder` / `NewsTranslator` Java text block 에 inline 으로 박혀 있어 문구 한 줄 수정에도 컴파일·재배포가 필요하고, Gemini 호출이 단발이라 일시적 5xx/네트워크 끊김이 그대로 사용자 fallback 으로 노출된다. |
| **Solution** | 시스템 프롬프트를 `apps/api/src/main/resources/prompts/{ai-signal,news-translate}.system.txt` 로 분리하고 기동 시 classpath 에서 로드(+캐싱). `GeminiLlmClient.generate()` 에 transient(5xx/429/IOException) 한정 1회 재시도(고정 250ms) 추가. |
| **Function/UX Effect** | 운영자: 프롬프트 튜닝을 PR 1줄로 가능 + Phase 3 이상에서 prompt versioning 기반 마련. 사용자: 일시적 LLM 장애 시 fallback 빈도 감소(예상 −30~50%). 개발자: text block escape 부담 제거. |
| **Core Value** | 코드/콘텐츠 분리 + 가용성 마진 확보 — Phase 2 archive index 의 deferred 항목 2건(프롬프트 외부화 / 재시도 1회 루프) 동시 해소로 Phase 3 진입 전 RAG 파이프라인 운영 안정도 마무리. |

---

## 1. Overview

### 1.1 Purpose

Phase 2 RAG 파이프라인 잔여 deferred 항목 2건을 단일 PDCA 사이클로 해소한다.

1. **프롬프트 외부화** — `PromptBuilder` / `NewsTranslator` 의 system 프롬프트 text block 을 classpath resource 로 추출.
2. **Gemini 재시도 1회 루프** — `GeminiLlmClient.generate()` 에 transient 실패 한정 1회 재시도(고정 백오프) 도입.

### 1.2 Background

- Phase 2 archive index (`docs/archive/2026-04/_INDEX.md`) phase2-rag-pipeline 후속 deferred 명시: "프롬프트 `resources/prompts/*.txt` 외부화, 재시도 1회 루프".
- PR #6 (Gemini 2.5 호환 fix) 이후 production 안정화는 끝났으나, 일회성 5xx (`UNAVAILABLE`, `INTERNAL`) 가 발생하면 즉시 fallback 응답으로 빠짐 — 메트릭 `llm_failure_count_total{reason="upstream"}` 으로 관찰됨.
- 프롬프트가 Java 소스에 묶여 있어 문구 1줄 수정에도 BE 재빌드/재배포 비용 발생. 향후 프롬프트 A/B (Phase 3+) 도입의 진입 장벽.

### 1.3 Related Documents

- 상위 설계: `docs/archive/2026-04/phase2-rag-pipeline/phase2-rag-pipeline.design.md` §6.1 / §6.2
- 직전 사이클: `docs/archive/2026-04/phase2.1-metrics-fe-refactor/phase2.1-metrics-fe-refactor.report.md`
- 코드 ref: `apps/api/src/main/java/com/aistockadvisor/ai/service/PromptBuilder.java`, `apps/api/src/main/java/com/aistockadvisor/news/service/NewsTranslator.java`, `apps/api/src/main/java/com/aistockadvisor/ai/infra/GeminiLlmClient.java`
- 면책 원칙: `docs/planning/07-legal-compliance.md`

---

## 2. Scope

### 2.1 In Scope

- [ ] `apps/api/src/main/resources/prompts/ai-signal.system.txt` 신설 (현 `PromptBuilder.SYSTEM_PROMPT_TEMPLATE` 1:1 이동, `%s` placeholder 유지)
- [ ] `apps/api/src/main/resources/prompts/news-translate.system.txt` 신설 (현 `NewsTranslator.SYSTEM_PROMPT_TEMPLATE` 1:1 이동)
- [ ] `PromptBuilder` / `NewsTranslator` 가 기동 시 classpath 에서 로드 + 첫 호출 시 `forbiddenTerms.quotedList()` 주입한 캐시 빌드 (현행 `cachedSystemPrompt volatile` 패턴 유지)
- [ ] 누락/IO 실패 시 `IllegalStateException` 으로 fail-fast (Phase 2.1 SSOT 원칙과 동일 — silent fallback 금지)
- [ ] `GeminiLlmClient.generate()` 재시도 1회 (transient 한정: HTTP 429 / 5xx / `IOException` / `WebClientRequestException`. 4xx·body 검증 실패는 즉시 실패)
- [ ] 백오프: 고정 250ms (jitter·exponential 미도입 — Resilience4j 도입 안 함, java.util.concurrent 만 사용)
- [ ] 메트릭: `llm.retry.count{feature, model, outcome=success|exhausted}` Counter 추가 (Phase 2.1 tag allowlist 확장)
- [ ] 단위 테스트: 프롬프트 로더 (성공/누락/UTF-8) 3건 + 재시도 (success/transient→retry success/transient→exhausted/non-transient skip) 4건

### 2.2 Out of Scope

- 프롬프트 외부화 후의 versioning / hot-reload (Spring `@RefreshScope`, Spring Cloud Config 등) — 다음 사이클
- jmustache / StringTemplate / Pebble 등 템플릿 엔진 도입 — Phase 2.1 Lessons "jmustache 미도입" 결정 유지, `String.formatted(...)` 그대로
- Resilience4j / Spring Retry 도입 — 단발 1회 재시도라 over-engineering
- exponential backoff / jitter — 단발 재시도라 불필요
- Circuit breaker — Phase 4+ (사용자 트래픽 증가 후 검토)
- 프롬프트 A/B 테스트 인프라 — 외부화로 토대만 마련하고 실제 분기 로직은 별도

---

## 3. Requirements

### 3.1 Functional Requirements

| ID | Requirement | Priority | Status |
|----|-------------|----------|--------|
| FR-01 | `classpath:prompts/ai-signal.system.txt` 가 존재하면 로드, 없으면 기동 시 명확한 예외(`IllegalStateException`, 메시지에 경로 포함) | High | Pending |
| FR-02 | `classpath:prompts/news-translate.system.txt` 동일 규칙 | High | Pending |
| FR-03 | 로드된 템플릿 + `forbiddenTerms.quotedList()` 조합 캐시는 첫 호출 후 재계산 금지 (현 `volatile` 캐시 보존) | High | Pending |
| FR-04 | `PromptBuilder.SYSTEM_PROMPT_TEMPLATE` / `NewsTranslator.SYSTEM_PROMPT_TEMPLATE` Java 상수 제거 (CI grep 으로 부재 검증) | High | Pending |
| FR-05 | `GeminiLlmClient.generate()` 가 transient 실패(`HttpStatus.is5xxServerError()` ∥ HTTP 429 ∥ `IOException`/`WebClientRequestException`) 시 250ms 후 1회만 재시도, 두 번째도 실패하면 원 예외 그대로 throw | High | Pending |
| FR-06 | 4xx (≠429), JSON 파싱 실패, 빈 본문, MAX_TOKENS finishReason 은 재시도 대상 아님 | High | Pending |
| FR-07 | `llm.retry.count{feature, model, outcome="success"\|"exhausted"}` Counter 가 Micrometer 에 등록되고 `/actuator/prometheus` 노출 (Phase 2.1 metric 6종 → 7종) | Medium | Pending |
| FR-08 | 재시도 발생 시 `WARN` 로그에 `feature={} model={} attempt=2 reason={}` 태그 일관 주입 (PR #6 로깅 컨벤션 준수) | Medium | Pending |

### 3.2 Non-Functional Requirements

| Category | Criteria | Measurement Method |
|----------|----------|-------------------|
| Performance | 프롬프트 로드 오버헤드 ≤ 5ms (기동 시 1회) | 단위 테스트 + `@PostConstruct` 로깅 |
| Performance | 재시도 추가 latency = 250ms × 재시도 횟수 (P99 영향 ≤ +250ms) | Phase 2.1 `llm.call.latency` Timer 비교 |
| Reliability | transient 5xx 1회 발생 시 사용자 응답 성공률 100% (MockWebServer 검증) | `GeminiLlmClientRetryTest` |
| Maintainability | 프롬프트 1줄 수정 시 Java 컴파일 0회 (resources 만 변경) | 수동 검증 + CI 로그 |
| Observability | `llm.retry.count` 가 outcome 라벨로 success/exhausted 분리 | `/actuator/prometheus` |
| Compliance | 외부화된 .txt 안에 forbidden 용어 직접 등장 금지 (네거티브 예시는 placeholder 로) | CI forbidden-terms scan (Phase 2.1 와 동일) |

---

## 4. Success Criteria

### 4.1 Definition of Done

- [ ] 2개 .txt 파일 신설 + 2개 Java 클래스의 inline 상수 삭제
- [ ] `GeminiLlmClient` 재시도 로직 + `llm.retry.count` Counter
- [ ] 단위 테스트 7건 추가 (loader 3 + retry 4) 모두 green, 기존 33+ tests regression 0
- [ ] `make api-check` (gradle check) 통과
- [ ] CI forbidden-terms scan 통과 (production source 범위)
- [ ] 로컬 검증: `make api-dev` 후 `/stocks/NVDA/ai-signal` 정상 응답 + Prometheus 에 `llm_retry_count_total` 노출

### 4.2 Quality Criteria

- [ ] Lint / static analysis 0 warning 신규 추가
- [ ] Build 시간 증가 ≤ 5%
- [ ] gap-detector Match Rate ≥ 90% (단일 사이클 완주)

---

## 5. Risks and Mitigation

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| classpath resource 누락으로 기동 실패 | High | Low | `@PostConstruct` 에서 즉시 로드 시도 + 명확한 예외 메시지 (경로 포함). 단위 테스트로 누락 케이스 검증 |
| 재시도가 LLM API 호출량을 2배로 늘려 quota 소진 가속 | Medium | Low | transient 한정 + 1회 cap. Phase 2.1 `llm.call.count` 메트릭으로 모니터링 (이상 시 즉시 disable) |
| 250ms 백오프가 thread 점유 → 동시성 저하 | Low | Low | 가상 스레드(Java 21 Loom) 사용 중이라 OS thread 점유 없음. 다만 첫 PR 후 P95 latency 1주 모니터링 |
| 외부화 .txt 문자열이 OS 별 줄바꿈 차이로 깨짐 | Medium | Low | 로더에서 UTF-8 + LF 강제 (기존 text block 동일). 단위 테스트에 CRLF 픽스처 포함 |
| forbidden-terms placeholder `%s` 가 `String.formatted` 와 충돌 (예: txt 안에 `%` 가 그대로 있을 때) | Medium | Medium | 로더에서 `%` 이스케이프 검증 → 단일 `%s` 외 발견 시 fail-fast |

---

## 6. Architecture Considerations

### 6.1 Project Level Selection

| Level | Characteristics | Recommended For | Selected |
|-------|-----------------|-----------------|:--------:|
| **Starter** | Simple structure | Static sites | ☐ |
| **Dynamic** | Feature-based modules, BaaS integration | Web apps with backend | ☑ (CLAUDE.md 고정) |
| **Enterprise** | Strict layer separation, microservices | High-traffic systems | ☐ |

### 6.2 Key Architectural Decisions

| Decision | Options | Selected | Rationale |
|----------|---------|----------|-----------|
| 프롬프트 저장 위치 | classpath / 외부 파일시스템 / DB / Spring Cloud Config | **classpath:prompts/** | 1인 개발·단일 BE 인스턴스 단계라 jar 패키징 안에서 충분. 외부 파일시스템은 Phase 4+ 다중 인스턴스 시 재검토 |
| 로드 시점 | 기동(`@PostConstruct`) / lazy 첫 호출 | **lazy + volatile cache** | 현행 `cachedSystemPrompt` 패턴 보존 (회귀 최소화). 기동 검증은 별도 health hook 으로 보강 |
| 템플릿 엔진 | `String.formatted` / jmustache / Pebble | **`String.formatted`** | Phase 2.1 archive index 에서 jmustache 미도입 결정 유지. 단일 `%s` placeholder 만 사용 |
| 재시도 라이브러리 | Spring Retry / Resilience4j / 직접 구현 | **직접 구현** | 1회 cap + 250ms 고정 → 의존성 추가 비용이 더 크다. `Mono.retryWhen` 또는 try-catch 1회 |
| 백오프 전략 | 고정 / exponential / jitter | **고정 250ms** | 단발 재시도라 차별 가치 없음. exponential 은 다회 재시도 도입 시 검토 |
| 메트릭 라벨 | `outcome=success/exhausted` 만 / reason 세분화 | **outcome 만** | Phase 2.1 tag allowlist (cardinality 최소화) 원칙 유지. reason 은 기존 `llm.failure.count{reason}` 로 충분 |

### 6.3 Folder Structure Preview

```
apps/api/src/main/
├── java/com/aistockadvisor/
│   ├── ai/
│   │   ├── infra/GeminiLlmClient.java     # +retry loop
│   │   └── service/PromptBuilder.java      # SYSTEM_PROMPT_TEMPLATE 제거 → loader 사용
│   ├── news/service/NewsTranslator.java    # SYSTEM_PROMPT_TEMPLATE 제거
│   └── common/prompt/PromptLoader.java     # 신규 (UTF-8 classpath loader, %s 검증)
└── resources/
    ├── prompts/                             # 신규
    │   ├── ai-signal.system.txt
    │   └── news-translate.system.txt
    └── legal/forbidden-terms.json           # 변경 없음
```

---

## 7. Convention Prerequisites

### 7.1 Existing Project Conventions

- [x] `CLAUDE.md` 코딩 컨벤션 (BE 패키지 layout, 환경변수 prefix)
- [x] Phase 2.1 메트릭 tag allowlist (feature/model/direction/reason/layer/outcome)
- [x] Phase 2.1 로그 컨벤션 (`feature={} model={}` 일관 주입)
- [ ] `apps/api/src/main/resources/prompts/` 디렉토리 (신설)

### 7.2 Conventions to Define/Verify

| Category | Current State | To Define | Priority |
|----------|---------------|-----------|:--------:|
| **Prompt 파일명** | 없음 | `{feature}.system.txt` (kebab-case + 역할 suffix) | High |
| **Prompt placeholder** | 없음 | 단일 `%s` 만 허용 (forbidden 용어 주입 1점) | High |
| **Prompt 인코딩** | 없음 | UTF-8 + LF, BOM 금지 | High |
| **메트릭 outcome 라벨** | success/parse/upstream/validation | success/exhausted (retry 전용) | Medium |
| **재시도 로그** | 없음 | `WARN feature={} model={} attempt=2 reason={}` | Medium |

### 7.3 Environment Variables Needed

| Variable | Purpose | Scope | To Be Created |
|----------|---------|-------|:-------------:|
| 신규 없음 | (외부화는 classpath 고정, 재시도는 코드 상수) | — | ☐ |

> 향후 retry 횟수/백오프를 환경별로 다르게 가져갈 필요가 생기면 `app.external.gemini.retry.{count,backoff-ms}` 추가 (Phase 2.2 시점은 hardcode `1`/`250ms`).

---

## 8. Next Steps

1. [ ] `/pdca design phase2.2-prompt-externalization` 실행 — `PromptLoader` 인터페이스, 재시도 시퀀스 다이어그램, 테스트 픽스처 명세 확정
2. [ ] feature 브랜치 생성: `feat/phase2.2-prompt-externalization` (PR 워크플로 정책)
3. [ ] 구현 → `make api-check` → `/pdca analyze` → ≥90% 시 `/pdca report` → squash merge

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 0.1 | 2026-04-15 | 초기 Plan 작성 (Phase 2 deferred 2건 단일 스코프) | wonseok-han + Claude |
