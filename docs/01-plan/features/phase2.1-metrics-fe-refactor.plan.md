# phase2.1-metrics-fe-refactor Planning Document

> **Summary**: Phase 2 RAG 파이프라인의 Launch Gate 4/4 는 통과했으나 남은 **Major gap 2건** (FR-15 Micrometer 미구현 + FE 폴더 편차)을 해소해 Match Rate 93% → 95%+ 로 끌어올리고, Phase 3 진입 전 **운영 관측성(Observability)** 과 **설계-구현 일치도**를 확보한다.
>
> **Project**: AI Stock Advisor
> **Version**: 0.2.1 (Phase 2.1 보강)
> **Author**: wonseok-han
> **Date**: 2026-04-14
> **Status**: Draft
> **Depends On**: `docs/archive/2026-04/phase2-rag-pipeline/` (Phase 2 완료 산출물)

---

## Executive Summary

| Perspective | Content |
|-------------|---------|
| **Problem** | Phase 2 Launch Gate 는 통과했지만 (1) Gemini 호출량·실패율·금지용어 탐지율을 **수치로 관측할 수단이 없어** 운영 중 이상 징후를 로그 grep 에 의존해야 하고, (2) FE 폴더 구조가 Design §5.3 (`features/stock-detail/{news,ai-signal}/`) 과 **편차**를 보여 종목 상세 하위 도메인 재사용·탐색 비용이 누적된다. |
| **Solution** | (1) Spring Boot Actuator + Micrometer **4종 counter/timer** (`llm.call.count`, `llm.token.total`, `llm.failure.count`, `llm.forbidden.hit.count`) 를 `GeminiClient` / `ResponseValidator` / `LegalGuardFilter` 에 주입하고 `/actuator/prometheus` 로 노출. (2) FE `features/news/`·`features/ai-signal/` 를 `features/stock-detail/{news,ai-signal}/` 하위로 **무기능 변경 리팩터**, import path 자동 교정. |
| **Function/UX Effect** | 운영자는 Prometheus(또는 Actuator JSON) 에서 **LLM 호출 볼륨·오류율·금지용어 탐지율**을 분 단위로 관측 가능 → 비용 상한 ($15/월) 초과 조기 감지. 개발자는 **폴더 구조 = 설계 문서 = 1:1** 이 되어 종목 상세 도메인 신규 기능(예: financials, peers) 추가 시 일관된 위치에 배치. 사용자 UI/API 응답에는 변화 없음. |
| **Core Value** | **"Launch Gate 통과 → 운영 준비 완료"**. Observability 를 Phase 3 (인증·북마크) 투입 전에 선행 구축해 LLM 비용 폭주·프롬프트 regression·금지용어 drift 를 **숫자로 감시**하는 체계를 확립. Match Rate 95%+ 로 Phase 2 PDCA 사이클을 실질적으로 닫는다. |

---

## 1. Overview

### 1.1 Purpose

Phase 2 PDCA 사이클에서 **비차단으로 이월**된 Major 2건을 단일 스코프로 묶어 처리한다. 신규 기능 추가가 아니라 **기술 부채 해소 + 관측성 주입 + 구조 정합성 복원**이 목적. Phase 2.1 종료 시:

1. `/actuator/prometheus` 에서 `llm_call_count`, `llm_token_total`, `llm_failure_count`, `llm_forbidden_hit_count` 4개 지표가 노출된다
2. FE `features/` 하위 구조가 `stock-detail/{chart, indicators, news, ai-signal}/` + `search/` 로 정리된다
3. Phase 2 Match Rate 93% → **95%+** 재검증 통과

### 1.2 Background (Phase 2 분석 결과 인용)

`docs/archive/2026-04/phase2-rag-pipeline/phase2-rag-pipeline.analysis.md` §"남은 Gap > Major":

- **FR-15 Micrometer counter 미구현** — Design §13.2 Step 20, v0.2-rc3 gate 항목. 로깅(`log.warn`) 은 있으나 counter 없음 → 운영 시 알림/임계치 설정 불가
- **FE 폴더 편차** — 구현 `features/news/`, `features/ai-signal/` vs Design §5.3 `features/stock-detail/{news,ai-signal}/`. 기능 동일, 폴더 재배치 시 95%+ 달성

두 항목 모두 **사용자 노출 동작 변경 없음**. 따라서 회귀 리스크가 낮고 테스트는 (1) Actuator 엔드포인트 assertion + (2) 기존 E2E 회귀 확인으로 충분.

### 1.3 Related Documents

- **Phase 2 Plan**: `docs/archive/2026-04/phase2-rag-pipeline/phase2-rag-pipeline.plan.md`
- **Phase 2 Design**: `docs/archive/2026-04/phase2-rag-pipeline/phase2-rag-pipeline.design.md` §5.3 (FE 구조), §13.2 Step 20 (Micrometer)
- **Phase 2 Analysis**: `docs/archive/2026-04/phase2-rag-pipeline/phase2-rag-pipeline.analysis.md`
- **Phase 2 Report**: `docs/archive/2026-04/phase2-rag-pipeline/phase2-rag-pipeline.report.md`
- **차후 Design**: `docs/02-design/features/phase2.1-metrics-fe-refactor.design.md` (다음 단계)

---

## 2. Scope

### 2.1 In Scope

- [ ] **BE-1: Micrometer 의존성** — `apps/api/build.gradle.kts` 에 `io.micrometer:micrometer-registry-prometheus` 추가, `spring-boot-starter-actuator` 확인
- [ ] **BE-2: Actuator 엔드포인트 노출** — `application.yml` `management.endpoints.web.exposure.include=health,info,prometheus,metrics`, `management.metrics.tags.application=ai-stock-advisor`
- [ ] **BE-3: 4종 지표 주입**
  - `llm.call.count` (Counter, tags: `feature`={news|ai-signal}, `model`=gemini-flash) — `GeminiClient` 호출 직전
  - `llm.token.total` (Counter, tags: `direction`={input|output}) — `GeminiClient` 응답 후 usageMetadata 파싱
  - `llm.failure.count` (Counter, tags: `feature`, `reason`={timeout|validation|forbidden|parse}) — `ResponseValidator` fallback 경로
  - `llm.forbidden.hit.count` (Counter, tags: `layer`={validator|filter}) — `ResponseValidator` + `LegalGuardFilter`
- [ ] **BE-4: MeterRegistry 주입** — 생성자 주입 방식(현 RestTemplate 패턴과 동일), 테스트 시 `SimpleMeterRegistry` 교체 가능
- [ ] **BE-5: 단위 테스트** — `MicrometerMetricsTest`: 각 counter 가 호출 경로 통과 시 +1 됨을 검증 (mock Gemini)
- [ ] **FE-1: 폴더 이동** — `apps/web/src/features/news/` → `apps/web/src/features/stock-detail/news/`, 동일하게 `ai-signal/`
- [ ] **FE-2: import path 교정** — 절대경로 `@/features/news/...` → `@/features/stock-detail/news/...` 전체 교체 (grep 기반)
- [ ] **FE-3: tsconfig path alias 확인** — 새로운 경로가 `baseUrl` + `paths` 로 해석되는지 `pnpm typecheck` 로 검증
- [ ] **Docs-1: Design 문서** — `docs/02-design/features/phase2.1-metrics-fe-refactor.design.md` 작성 (다음 PDCA 단계)
- [ ] **CI/CD** — 기존 `forbidden-terms.yml` 유지, Actuator 엔드포인트 노출로 인한 민감정보 누출 없음 확인 (health/info 외 prometheus 만 노출)

### 2.2 Out of Scope (Phase 3 이관)

- ❌ **Minor gaps**: 프롬프트 외부화(`resources/prompts/*.txt`), ResponseValidator 재시도 1회 루프, rationale 3~5개 강제, V4 `idx_ai_signal_audit_fallback` 인덱스, 입력 토큰 clipping — **Phase 3 설계 단계**에서 재평가
- ❌ **Prometheus/Grafana 인프라 구축** — 지표 노출만 담당, 수집·대시보드는 배포 환경(Fly.io/Oracle) 선정 후
- ❌ **알림(AlertManager)/SLO 정의** — Phase 4 운영 단계
- ❌ **FE 외 리팩터** (예: `stock-detail-view.tsx` 분할) — 폴더 이동과 무관한 변경은 금지

---

## 3. Goals & Success Criteria

### 3.1 Goals

| ID | Goal | Metric |
|---|---|---|
| G-1 | LLM 관측성 확보 | `/actuator/prometheus` 에서 4개 지표 노출, 각 tag 조합 유효 |
| G-2 | FE 구조 정합성 | Design §5.3 ↔ 실제 폴더 1:1 일치, `pnpm typecheck` + `pnpm lint` 통과 |
| G-3 | Match Rate 복구 | Phase 2 gap-detector 재실행 시 **95%+** |
| G-4 | 회귀 0건 | 뉴스·AI 시그널 기존 E2E (Phase 2 archive 의 do 기록) 동일 동작 |

### 3.2 Success Criteria (Done Definition)

- [ ] `curl localhost:8080/actuator/prometheus | grep llm_` 로 4개 지표 확인
- [ ] 단위 테스트 `MicrometerMetricsTest` 통과 (각 counter 카운트 증가 assert)
- [ ] `apps/web/src/features/news/`, `apps/web/src/features/ai-signal/` 디렉토리 부재
- [ ] `apps/web/src/features/stock-detail/news/`, `apps/web/src/features/stock-detail/ai-signal/` 존재
- [ ] `pnpm --filter web typecheck` + `pnpm --filter web lint` + `./gradlew check` 전부 통과
- [ ] `make check` green
- [ ] `/pdca analyze phase2.1-metrics-fe-refactor` 재실행 시 Match Rate ≥ 95%

---

## 4. Milestones

| ID | Milestone | ETA | Criteria |
|---|---|---|---|
| M-1 | Design 문서 작성 | T+1d | `docs/02-design/features/phase2.1-metrics-fe-refactor.design.md` 완성, Spring Actuator + Micrometer 주입 지점 명시 |
| M-2 | BE Micrometer 주입 | T+2d | 4개 counter 주입 + 단위 테스트 + `/actuator/prometheus` 로컬 확인 |
| M-3 | FE 폴더 재배치 | T+1d | git mv + import 교체 + typecheck/lint green |
| M-4 | Gap 재검증 + Report | T+1d | Match Rate 95%+ 확인, PR 머지 |

**총 예상**: ~5일 (1인 개발, 반나절 단위 Step)

---

## 5. Risks & Mitigations

| Risk | Impact | Likelihood | Mitigation |
|---|---|---|---|
| Actuator 엔드포인트 노출 과다 (`env`, `beans` 등) | Medium | Low | `exposure.include` 에 `health,info,prometheus,metrics` 만 명시 (allowlist) |
| Micrometer counter tag cardinality 폭발 (예: ticker 를 tag 로) | Medium | Low | ticker 는 **절대 tag 에 넣지 않음**, feature/model/direction/reason/layer 만 사용 |
| FE 폴더 이동 중 import 누락 | High (빌드 실패) | Medium | `grep -rn "@/features/news\|@/features/ai-signal"` 로 사전 수집 → sed 교체 → typecheck 반복 |
| Next.js 16 예약 경로 충돌 | Low | Low | `features/` 하위는 Next.js 예약 경로 아님, `app/` 만 해당 |
| MeterRegistry 테스트 시 싱글톤 상태 오염 | Low | Medium | 각 테스트 `@BeforeEach` 에서 `SimpleMeterRegistry` 신규 생성 |
| 지표 이름 규칙 일관성 누락 | Low | Low | Micrometer 관례 `<component>.<unit>[.suffix]` 준수, dot-notation 소문자 통일 |

---

## 6. Dependencies

### 6.1 Internal

- Phase 2 구현 (`apps/api/**`, `apps/web/**`) — main 브랜치 기준 `16dfaa7` 이후
- `LegalGuardFilter`, `ResponseValidator`, `GeminiClient` 클래스 존재 확인 (아카이브 Do 문서 참조)

### 6.2 External

- `io.micrometer:micrometer-registry-prometheus` (Spring Boot 3.5.13 번들 호환, 별도 버전 고정 불필요)
- `spring-boot-starter-actuator` (이미 의존성에 포함 추정, 확인 필요)

### 6.3 없음 (명시적 Non-Dependency)

- Prometheus 서버, Grafana, AlertManager — 노출만 담당
- 외부 SaaS (Datadog, New Relic) — Phase 4+ 재검토

---

## 7. Open Questions

| # | Question | Owner | Target |
|---|---|---|---|
| Q-1 | ticker 단위 관측이 필요한가? (현 설계는 feature/model 만 tag) | wonseok-han | Design 단계 결정. 기본 No, 필요 시 별도 고-카디널리티 지표 분리 |
| Q-2 | `/actuator/*` 전체를 public 으로 노출할지, internal Admin 네트워크로 제한할지? | wonseok-han | Phase 4 배포 시점. Phase 2.1 은 기본 public (health/info/prometheus/metrics 4종만) |
| Q-3 | FE 폴더 이동 시 `git mv` vs 수동 이동? | — | `git mv` 강제 — blame 유지 |

---

## 8. References

- Phase 2 Archive: `docs/archive/2026-04/phase2-rag-pipeline/`
- Phase 2 Report §"후속 (Phase 2.1/3)": `docs/archive/2026-04/_INDEX.md`
- Micrometer 공식 가이드: https://micrometer.io/docs/concepts
- Spring Boot Actuator: `management.endpoints.web.exposure.include` 설정
- 프로젝트 규칙: `CLAUDE.md` (파일명 kebab-case, 환경변수 prefix)

---

**Next Step**: `/pdca design phase2.1-metrics-fe-refactor`
