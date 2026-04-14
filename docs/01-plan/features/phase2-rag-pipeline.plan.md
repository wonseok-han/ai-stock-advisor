# phase2-rag-pipeline Planning Document

> **Summary**: Phase 1 MVP "보여주기" → Phase 2 "이해시키기" 전환. 종목 뉴스 수집 + LLM 한국어 요약 (F5) + RAG 기반 AI 종합 시그널 (F4) + 4-level 금지용어 가드 + `/detail` hydrate.
>
> **Project**: AI Stock Advisor
> **Version**: 0.2 (Phase 2 착수)
> **Author**: wonseok-han
> **Date**: 2026-04-14
> **Status**: Draft
> **PRD**: `docs/00-pm/phase2-rag-pipeline.prd.md` (438줄, 8-section)

---

## Executive Summary

| Perspective | Content |
|-------------|---------|
| **Problem** | Phase 1 이 차트·지표·검색을 한국어로 "보여주기" 까지는 성공했으나, 초보 투자자는 여전히 **영어 뉴스 원문 + 여러 지표 종합 판단** 부담을 본인 머릿속에서 조립해야 한다. 분석은 여전히 사용자 몫. |
| **Solution** | 종목 뉴스 수집 (Finnhub `/company-news`) + Gemini Flash 한국어 3줄 요약 + RAG 기반 AI 종합 시그널 JSON (signal·confidence·rationale·risks) 을 Phase 1 종목 상세 페이지에 부착. **4-level 금지용어 가드** (상수/프롬프트/validator/CI) 로 자본시장법 경계 방어. |
| **Function/UX Effect** | 종목 상세 체류 +50% (60s → 90s+), AI 시그널 섹션 도달률 ≥ 60%, LLM 캐시 hit ≥ 70%, 월 LLM 비용 ≤ $15. `/detail` 엔드포인트가 Phase 1 scaffold (`news=null, aiSignal=null`) 에서 실데이터 hydrate. |
| **Core Value** | **"보여주기 → 이해시키기" 전환**. 3분 안에 한국어로 "분위기 + 근거 + 리스크" 설명받음. 할루시네이션·프롬프트 인젝션·자본시장법 리스크를 RAG + 금지용어 가드 + 이중 캐시로 동시에 관리. |

---

## 1. Overview

### 1.1 Purpose

Phase 1 MVP 이 완결한 "데이터를 한국어로 보여주기" 를 **"데이터를 한국어로 이해시키기"** 로 진화시킨다. Phase 2 종료 시점(v0.2 Public)에 다음이 성립해야 한다.

1. **뉴스 레이어**: 종목당 최근 7일 5~10건 뉴스가 한국어 3줄 요약 + 감성 태그로 종목 상세에 표시
2. **AI 시그널 레이어**: 지표·뉴스·시장맥락을 서버측 조립(RAG) → Gemini Flash JSON → AI 카드 렌더링 (면책 카드 내부 고정)
3. **4-level 금지용어 가드**: 코드 상수 / 프롬프트 / ResponseValidator / CI grep 4개 레이어로 "매수·매도 권유" 출력 차단
4. **법적 안전성 유지**: 유사투자자문업 미신고 합법 운영 (무료 유지 + "분석 도구" 포지셔닝 + 면책 100% 커버리지)

### 1.2 Background

상세는 PRD §1.1, §5.2 참조. 3줄 요약:
- Phase 1 Match Rate 94% 달성 (2026-04-14) — `mvp.design.md` 의 `/news`, `/ai-signal` 엔드포인트는 Phase 2 scope 로 이미 문서화됨
- Persona 1 pilot 관찰상 "기술지표보다 뉴스 요약을 더 자주 본다" → **F5 가 F4 의 선행 피처**로 재정의
- 법적 리스크는 AI 판단 출력 도입 시 질적으로 증가 → 4-level 가드를 Phase 2 내에 완성해야 v1.0 Public Launch 가능

### 1.3 Related Documents

- **PRD**: `docs/00-pm/phase2-rag-pipeline.prd.md`
- **Phase 1 설계 (연속성)**: `docs/archive/2026-04/mvp/mvp.design.md` §3.5 (RAG 컴포넌트), §4.1 (`/news`, `/ai-signal` 엔드포인트), §4.4 (forbidden-terms)
- **Phase 1 회고 (배운 점)**: `docs/archive/2026-04/mvp/mvp.report.md`
- **AI 전략**: `docs/planning/05-ai-strategy.md`
- **법적 고지**: `docs/planning/07-legal-compliance.md`
- **프로젝트 컨벤션**: `CLAUDE.md`
- **차후 Design 문서**: `docs/02-design/features/phase2-rag-pipeline.design.md` (다음 단계)

---

## 2. Scope

### 2.1 In Scope (Phase 2 = v0.2-alpha ~ v0.2 Public)

- [ ] **F5-a 뉴스 수집** — Finnhub `/company-news` 어댑터 + Postgres 24h 캐시 (news_raw 테이블)
- [ ] **F5-b 뉴스 LLM 요약** — Gemini Flash: `title_ko`, `summary_ko` (3줄), `sentiment` (positive/neutral/negative)
- [ ] **F4 AI 시그널** — RAG: ContextAssembler (지표+뉴스+시장) → PromptBuilder → GeminiClient → ResponseValidator → JSON (`signal`, `confidence`, `timeframe`, `rationale[]`, `risks[]`, `summary_ko`)
- [ ] **RAG 컴포넌트** — ContextAssembler / PromptBuilder / GeminiClient / ResponseValidator 서비스 레이어
- [ ] **4-level 금지용어 가드**
  - L1: `apps/api/src/main/resources/forbidden-terms.json` (상수)
  - L2: 시스템 프롬프트 강제 규칙 ("가능성·경향 어조", "매수·매도 권유 금지")
  - L3: `LegalGuardFilter` (Servlet) + ResponseValidator (재시도 1회 후 중립 fallback + 감사 로그)
  - L4: `.github/workflows/forbidden-terms.yml` CI grep (코드·UI 텍스트·프롬프트 템플릿)
- [ ] **API 엔드포인트 신규**
  - `GET /api/v1/stocks/{ticker}/news` — 뉴스 리스트 (한국어 요약 포함)
  - `GET /api/v1/stocks/{ticker}/ai-signal` — AI 시그널 JSON
- [ ] **`/detail` hydrate** — Phase 1 scaffold 의 `news=null, aiSignal=null` 필드를 실제 데이터로 채움 (Spring `@Async` 가상 스레드 parallel)
- [ ] **DB 스키마** — Flyway V2/V3: `news_raw` (뉴스 원문 + 요약 캐시), `ai_signal_audit` (감사 로그)
- [ ] **캐시 정책**
  - 뉴스: Postgres 24h (기사 단위, 동일 원문 재번역 $0)
  - AI 시그널: Redis 1h (종목 단위) + Postgres 감사 영구
- [ ] **FE 컴포넌트 신규**
  - `features/stock-detail/news/news-panel.tsx` (뉴스 카드 리스트 + 원문 토글 + 감성 배지 + 면책)
  - `features/stock-detail/ai-signal/ai-signal-panel.tsx` (signal 배지 + confidence 게이지 + rationale/risks + 면책 카드 내부 고정)
- [ ] **비용/레이트 모니터링** — Micrometer counter (LLM 호출수/토큰/실패/금지용어 검출), `/actuator/metrics` 노출
- [ ] **partial response** — 뉴스만 성공 · AI 실패 시에도 UI 부분 hydrate (Phase 1 scaffold 의 graceful degradation 패턴 유지)
- [ ] **면책 UX 강화** — AI 카드 내부 고정 면책 + confidence 인라인 툴팁 + 뉴스 하단 "AI 자동 생성" 고지

### 2.2 Out of Scope (Phase 3+)

- **F6 시장 대시보드** — 지수/VIX/환율/시장 뉴스 (Phase 3)
- **F7 북마크 + Auth** — Supabase Auth + Spring JWT (Phase 4)
- **F8 푸시 알림** — Web Push API + 스케줄러 (Phase 4)
- **F9 용어사전 페이지** — RSI/MACD/볼밴 정적 페이지 (Phase 2 여유 시 only, 기본 Phase 3)
- **F10 시장 일일 브리핑** — AI 자동 생성 일일 요약 (Phase 3+)
- **유료 플랜** — 자본시장법 재검토 후에만 (Phase 5+)
- **타 LLM 제공자** — GPT-4o-mini/Claude (인터페이스만 준비, 실제 교체는 비용·품질 트리거 시)
- **벡터 DB / pgvector** — 현재 RAG 는 structured context (JSON) 조립이라 벡터 검색 불필요. 뉴스 증가 시 Phase 3+ 검토

---

## 3. Requirements

### 3.1 Functional Requirements

| ID | Requirement | Priority | Status |
|----|-------------|----------|--------|
| FR-01 | `/stocks/{ticker}/news` — 최근 7일 뉴스 리스트 (5~10건), 한국어 title/summary + sentiment + 원문 링크 | High | Pending |
| FR-02 | 뉴스 LLM 번역·요약 — Gemini Flash, 3줄 요약, 원문 주체·감성 보존 프롬프트 규칙 | High | Pending |
| FR-03 | 뉴스 Postgres 24h 캐시 — 동일 기사 재번역 방지 (Article URL hash 기반 키) | High | Pending |
| FR-04 | `/stocks/{ticker}/ai-signal` — RAG 기반 JSON 응답 (signal/confidence/timeframe/rationale/risks/summary_ko) | High | Pending |
| FR-05 | ContextAssembler — 지표 스냅샷 + 뉴스 + 시장맥락을 single JSON 으로 조립 | High | Pending |
| FR-06 | PromptBuilder — 시스템 프롬프트 + context + 금지용어 규칙 + 면책 문구 강제 삽입 | High | Pending |
| FR-07 | GeminiClient — JSON mode (`responseMimeType: application/json`) + 재시도 1회 + timeout 5s | High | Pending |
| FR-08 | ResponseValidator — JSON 스키마 검증 + 금지용어 검출 시 재시도 1회 → 중립 fallback + 감사 로그 | High | Pending |
| FR-09 | AI 시그널 Redis 1h 캐시 (종목 단위) + Postgres `ai_signal_audit` 영구 감사 | High | Pending |
| FR-10 | `/detail` hydrate — Phase 1 scaffold 의 news/aiSignal 필드를 실데이터로 (가상 스레드 parallel) | High | Pending |
| FR-11 | FE NewsPanel — 카드 리스트, 원문 토글, 감성 배지, "AI 자동 생성" 고지 | High | Pending |
| FR-12 | FE AiSignalPanel — signal 배지, confidence 게이지, rationale/risks 리스트, 고정 면책 | High | Pending |
| FR-13 | 4-level 금지용어 가드 — forbidden-terms.json + 프롬프트 + validator + CI grep | High | Pending |
| FR-14 | LegalGuardFilter (Servlet) — 응답 body 에 금지용어 남아있으면 500 + 감사 로그 | High | Pending |
| FR-15 | 비용 모니터링 — Micrometer counter (LLM 호출/토큰/실패/금지용어 검출) | Medium | Pending |
| FR-16 | partial response — 뉴스만 성공 · AI 실패 시에도 UI 부분 hydrate | Medium | Pending |
| FR-17 | Bucket4j rate limit (분 15 RPM) — Gemini 동시 요청 직렬화 | Medium | Pending |
| FR-18 | 뉴스 감성 태그 (positive/neutral/negative) + FE 배지 UI | Medium | Pending |
| FR-19 | Resilience4j circuit breaker — Gemini 호출 실패 연속 시 circuit open | Medium | Pending |
| FR-20 | 레드팀 prompt injection 테스트 20 케이스 — 티커 입력에 악의적 지시 | High | Pending |

### 3.2 Non-Functional Requirements

| Category | Criteria | Measurement Method |
|----------|----------|-------------------|
| **Performance** | AI 시그널 p95 응답 시간 (cache hit) < 200ms | Micrometer `http.server.requests` |
| | AI 시그널 p95 응답 시간 (cache miss) < 5s | Micrometer |
| | 뉴스 리스트 p95 응답 시간 (cache hit) < 100ms | Micrometer |
| **Reliability** | Gemini JSON 파싱 실패율 < 2% | ResponseValidator 실패 카운터 |
| | 캐시 hit ratio ≥ 70% (Redis + Postgres 합산) | Prometheus gauge |
| **Security** | prompt injection 방어 100% (20 레드팀 케이스) | @SpringBootTest + 레드팀 픽스처 |
| | forbidden-terms 검출 = 0 (response validator + CI grep) | CI workflow + 통합 테스트 |
| | LLM 응답 JSON 스키마 위반 = 0 (fallback 후) | ResponseValidator 단위 테스트 |
| **Cost** | 월 LLM 비용 ≤ $15 (MAU 1K 기준) | Gemini billing + Micrometer 토큰 카운터 |
| **Legal/Compliance** | 면책 문구 노출 100% (AI 카드 + 뉴스 하단 + summary_ko 말미) | FE snapshot 테스트 + 통합 테스트 |
| | 뉴스 요약 왜곡 ≤ 5% (파일럿 50건 수동 스팟체크) | 수동 QA |
| **Usability** | 종목 상세 체류 +50% (60s → 90s+) | (측정 인프라 Phase 2 는 placeholder) |
| | AI 시그널 섹션 스크롤 도달률 ≥ 60% | (측정 인프라 Phase 2 는 placeholder) |
| **Accessibility** | WCAG 2.1 AA — AI 카드/뉴스 카드 키보드 내비게이션 + aria-label | Lighthouse a11y score ≥ 90 |

---

## 4. Success Criteria

### 4.1 Definition of Done

- [ ] FR-01 ~ FR-20 모두 구현 + 단위/통합 테스트 통과
- [ ] BE: `./gradlew check` 통과 (Checkstyle + SpotBugs + JUnit + JaCoCo)
- [ ] FE: `pnpm lint && pnpm typecheck && pnpm build` 통과
- [ ] gap-detector 의 Match Rate ≥ 90%
- [ ] 4-level 금지용어 가드 4/4 레이어 구축 및 CI 통과
- [ ] 레드팀 20 케이스 prompt injection 방어율 100%
- [ ] 뉴스 요약 파일럿 50건 수동 스팟체크 왜곡 ≤ 10% (Phase 2 종료), ≤ 5% (+90일)
- [ ] LLM JSON 파싱 실패율 < 2%
- [ ] `/detail` hydrate 통합 테스트 (@SpringBootTest + WireMock) 통과
- [ ] FE NewsPanel/AiSignalPanel 면책 문구 snapshot 테스트 통과
- [ ] Phase 2 완료 리포트 (`docs/04-report/phase2-rag-pipeline.report.md`) 작성

### 4.2 Quality Criteria

- [ ] BE 테스트 coverage ≥ 70% (Phase 1 기준 유지)
- [ ] FE typecheck 0 errors
- [ ] Zero lint errors (ESLint + Checkstyle)
- [ ] Build succeeds (FE Next.js production build + BE Gradle bootJar)
- [ ] Lighthouse a11y score ≥ 90 (AI 시그널 카드 + 뉴스 카드)
- [ ] 1주 실측 운영: 월 LLM 비용 추정 ≤ $15, 캐시 hit ≥ 65%

### 4.3 Launch Gate (v0.2 Public 진입 조건)

- [ ] 4.1 Definition of Done 모두 체크
- [ ] 4.2 Quality Criteria 모두 체크
- [ ] 10명 내부 파일럿 만족도 ≥ 4.0 / 5.0
- [ ] 변호사 30분 리뷰 완료 (LLM 출력 샘플 10건 동봉)
- [ ] Finnhub/TwelveData TOS 재검토 (뉴스 재가공 허용 여부)
- [ ] PR 머지 완료 + squash merge → main

---

## 5. Risks and Mitigation

(상세는 PRD §6 참조, 여기는 구현 단계에서 트리거할 수 있는 구체 mitigation 만 나열)

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| **R1. 자본시장법 해석 변경** — AI signal 출력이 "유사투자자문업" 경계에 접근 | 최대 | Low-Med | (a) 무료 유지 (b) 4-level 가드 (c) 면책 100% (d) v1.0 Public 전 변호사 30분 리뷰 |
| **R2. confidence 숫자 오독** — "0.68 = 68% 상승 확률" 로 오해 | 큼 | Med-High | (a) 인라인 툴팁 (b) summary_ko 말미 면책 강제 (c) signal 표현 재검토 ("강한 매수" → "강한 상승 신호") |
| **R3. LLM 품질·비용·가용성** — Gemini 무료 티어 변경, JSON 파싱 실패 | 중 | Med | (a) `LlmClient` 인터페이스 (GPT-4o-mini 교체) (b) Bucket4j + Redis 큐 (c) 이중 캐시 (d) Resilience4j circuit breaker |
| **R4. 뉴스 LLM 요약 왜곡** — 원문과 다른 요약 | 중 | Med | (a) 프롬프트 "주체·감성 보존" 규칙 (b) 파일럿 50건 수동 스팟체크 (c) sentiment 구조화로 객관성 강제 |
| **R5. prompt injection** — 티커 입력에 악의적 지시 | 중 | Med | (a) 티커 regex 화이트리스트 (b) context 경계 마커 (c) 레드팀 20 케이스 |
| **R6. Finnhub 뉴스 커버리지 공백** — 중소형주 뉴스 부족 | 작음 | Med-High | (a) 대형주 우선 scope (b) 뉴스 비어도 AI signal 독립 생성 (c) Phase 3 RSS 보조 검토 |
| **R7. Gemini 분 15 RPM** — 동시 요청 스파이크 | 작음 | Low-Med | (a) Bucket4j (b) Redis 큐 (c) 캐시 hit ratio 70% 유지 |
| **R8. Phase 1 `/detail` scaffold 계약 파기** — hydrate 시 응답 shape 변경 | 중 | Low | (a) Phase 1 response shape 엄격 유지 (b) news/aiSignal 필드만 null → 실값 (c) 통합 테스트로 회귀 방어 |

---

## 6. Architecture Considerations

### 6.1 Project Level Selection

| Level | Characteristics | Recommended For | Selected |
|-------|-----------------|-----------------|:--------:|
| Starter | Simple structure | Static sites | ☐ |
| **Dynamic** | **Feature-based modules, Spring Boot + Next.js** | **Web apps with backend, SaaS MVPs** | **☑** |
| Enterprise | Strict layer separation, DI, microservices | High-traffic systems | ☐ |

**Rationale**: Phase 1 에서 이미 Dynamic 확정. Phase 2 는 동일 구조 유지 + 서비스 레이어 확장.

### 6.2 Key Architectural Decisions

| Decision | Options | Selected | Rationale |
|----------|---------|----------|-----------|
| **LLM 공급자** | Gemini 1.5 Flash / GPT-4o-mini / Claude Haiku | **Gemini 1.5 Flash** | 무료 티어 일 1M 토큰 / JSON mode 안정 / Phase 1 전략 문서에 이미 확정. `LlmClient` 인터페이스로 교체 경로 유지 |
| **뉴스 소스** | Finnhub /company-news / Alpha Vantage / RSS | **Finnhub /company-news** (Must) + RSS 보조 (Should, Phase 3) | Phase 1 Finnhub 키 재활용, 대형주 커버리지 충분. 중소형주 공백은 Phase 3 RSS 로 |
| **뉴스 캐시** | Redis / Postgres / In-memory | **Postgres 24h** | 동일 기사 재번역 방지 필요 → 영속성 필수 (Redis TTL 은 1h 수준이라 부적합). 테이블 `news_raw` 신규 |
| **AI 시그널 캐시** | Redis 1h / Postgres / 미적용 | **Redis 1h (primary) + Postgres 영구 감사** | 빠른 hit (cache 목적) + 감사 로그 영속 분리 |
| **RAG 구조** | Vector DB / Structured JSON context | **Structured JSON context** (pgvector 미도입) | 뉴스 5~10건 + 지표 스냅샷 규모라 벡터 검색 불필요. Phase 3+ 검토 |
| **JSON 스키마 강제** | responseMimeType / schema 정의 / 자유 텍스트 | **responseMimeType=application/json + validator** | Gemini JSON mode + 자체 validator 이중 방어 |
| **금지용어 가드** | 단일 레이어 / 다층 | **4-level (상수+프롬프트+validator+CI)** | PRD §7.1 명시. 단일 레이어는 법적 방어 불충분 |
| **rate limit** | 없음 / Bucket4j / Resilience4j | **Bucket4j + Resilience4j circuit breaker** | 분 15 RPM + 연속 실패 차단 이중 |
| **Concurrency** | Thread pool / Virtual threads | **Virtual threads (Java 21)** | Phase 1 확정, `/detail` parallel 에 재활용 |
| **FE 상태** | React Query + Zustand | **React Query (server) + Zustand (client)** | Phase 1 확정 유지 |

### 6.3 Clean Architecture Approach

**Selected Level**: Dynamic (Phase 1 과 동일)

```
apps/api/src/main/java/com/aistockadvisor/
├── stock/                      (Phase 1)
│   ├── domain/                 IndicatorSnapshot, Quote, Candle, SearchHit
│   ├── service/
│   └── web/
├── news/                       (Phase 2 신규)
│   ├── domain/                 NewsItem (title, title_ko, summary_ko, sentiment, sourceUrl, publishedAt)
│   ├── service/                NewsService, NewsTranslator (LLM)
│   ├── infra/                  FinnhubNewsClient, NewsRawRepository (JPA)
│   └── web/                    NewsController
├── ai/                         (Phase 2 신규)
│   ├── domain/                 AiSignal (signal, confidence, timeframe, rationale[], risks[], summary_ko)
│   ├── service/                AiSignalService, ContextAssembler, PromptBuilder, ResponseValidator
│   ├── infra/                  GeminiClient (impl of LlmClient), AiSignalAuditRepository
│   └── web/                    AiSignalController
├── legal/                      (Phase 2 신규)
│   ├── ForbiddenTermsRegistry  (forbidden-terms.json 로딩)
│   └── LegalGuardFilter        (Servlet filter — 응답 body 검사)
└── common/                     (Phase 1 유지)
    ├── cache/                  RedisCacheAdapter
    └── error/

apps/web/src/features/stock-detail/
├── news/                       (Phase 2 신규)
│   ├── news-panel.tsx
│   ├── news-card.tsx
│   └── use-stock-news.ts       (React Query)
└── ai-signal/                  (Phase 2 신규)
    ├── ai-signal-panel.tsx
    ├── signal-badge.tsx
    ├── confidence-gauge.tsx
    └── use-ai-signal.ts
```

---

## 7. Convention Prerequisites

### 7.1 Existing Project Conventions

Check which conventions already exist in the project:

- [x] `CLAUDE.md` has coding conventions section (FE kebab-case + BE package.dot)
- [ ] `docs/01-plan/conventions.md` exists (Phase 2 에서 필요 여부 검토 — Phase 1 컨벤션 계승)
- [x] `CONVENTIONS.md` at project root — **없음** (CLAUDE.md 로 대체 중, Phase 2 는 유지)
- [x] ESLint configuration — `apps/web/eslint.config.mjs`
- [x] Prettier configuration — `apps/web/.prettierrc.json`
- [x] TypeScript configuration — `apps/web/tsconfig.json`
- [x] Checkstyle — `apps/api/config/checkstyle/checkstyle.xml`

### 7.2 Conventions to Define/Verify (Phase 2 추가 사항)

| Category | Current State | To Define | Priority |
|----------|---------------|-----------|:--------:|
| **FE 파일명** | kebab-case (CLAUDE.md 확정) | 신규 파일 모두 kebab-case 유지 | High |
| **BE 패키지** | `com.aistockadvisor.<domain>` (Phase 1 확정) | `news`, `ai`, `legal` 신규 추가 | High |
| **LLM 프롬프트 위치** | (신규) | `apps/api/src/main/resources/prompts/*.txt` 로 분리 (hot swap + grep 용이) | High |
| **금지용어 레지스트리** | (신규) | `apps/api/src/main/resources/forbidden-terms.json` — 한국어/영어 이중 목록 | High |
| **AI 시그널 DTO 네이밍** | Phase 1 `IndicatorSnapshot.movingAverage` 규약 계승 | `AiSignal.summaryKo` (camelCase, JSON 직렬화 그대로) | High |
| **API 응답 면책 필드** | (신규) | `disclaimer: string` 필드 모든 LLM 출력에 강제 포함 | High |
| **에러 코드** | Phase 1 `ApiErrorCode` 계승 | `NEWS_SOURCE_UNAVAILABLE`, `AI_GENERATION_FAILED`, `FORBIDDEN_TERM_DETECTED` 신규 | Medium |

### 7.3 Environment Variables Needed

| Variable | Purpose | Scope | New? |
|----------|---------|-------|:----:|
| `GEMINI_API_KEY` | Gemini 1.5 Flash API 키 | Server | ☑ (기존 placeholder 활성화) |
| `GEMINI_MODEL` | 모델명 (기본 `gemini-1.5-flash`) | Server | ☑ |
| `GEMINI_TIMEOUT_MS` | LLM 타임아웃 (기본 5000) | Server | ☑ |
| `GEMINI_RPM_LIMIT` | Bucket4j 분 RPM 리밋 (기본 15) | Server | ☑ |
| `FINNHUB_API_KEY` | 뉴스 수집용 (Phase 1 기존) | Server | — (기존) |
| `NEWS_CACHE_TTL_HOURS` | 뉴스 Postgres 캐시 TTL (기본 24) | Server | ☑ |
| `AI_SIGNAL_CACHE_TTL_SECONDS` | AI 시그널 Redis TTL (기본 3600) | Server | ☑ |
| `FORBIDDEN_TERMS_PATH` | 금지용어 목록 (기본 classpath:forbidden-terms.json) | Server | ☑ |

### 7.4 Pipeline Integration

9-phase Development Pipeline 은 Phase 1 에서 적용했으므로 Phase 2 는 **feature 단위 PDCA** 만 적용. Pipeline Phase 1 (Schema) 은 `news_raw`, `ai_signal_audit` 테이블 추가로 증분 반영 (Flyway V2, V3 마이그레이션).

---

## 8. Next Steps

1. [ ] **`/pdca design phase2-rag-pipeline`** — 이 Plan 을 참조하여 Design 문서 작성
   - RAG 컴포넌트 간 계약 (DTO / 예외 / 캐시 키 / 감사 스키마)
   - Flyway V2/V3 마이그레이션 구체안 (`news_raw`, `ai_signal_audit` 컬럼 설계)
   - 프롬프트 텍스트 초안 (시스템 프롬프트 + few-shot + 금지용어 규칙)
   - FE 컴포넌트 와이어 (NewsPanel / AiSignalPanel)
   - `forbidden-terms.json` 초안 목록 (한/영 이중)
   - 레드팀 prompt injection 케이스 20개 명세
2. [ ] **Design 승인 → `/pdca do phase2-rag-pipeline`** — 구현 착수
3. [ ] **병행: MVP 후속 조치 소진** (MVP report §7.1)
   - m-2: FE `SearchHit.exchange: string | null` 타입 보정
   - m-1: `Quote.volume` Twelve Data hydrate 결정
4. [ ] **법적 사전 준비**
   - 변호사 30분 리뷰 미팅 D+30 스케줄링
   - 금지용어 초안 목록 확정 (Design 단계)

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 0.1 | 2026-04-14 | Initial draft (PRD §5.6 / §5.7 / §5.8 기반) | wonseok-han |
