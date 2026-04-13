# mvp Planning Document

> **Summary**: 초보 한국어 라이트 투자자용 미국 주식 통합 분석 대시보드 MVP — F1(검색) · F2(차트) · F3(지표) · F4(AI 시그널) · F5(뉴스 번역) · F6(시장 대시보드) + F9(용어사전, 가능 시).
>
> **Project**: AI Stock Advisor
> **Version**: 0.1 (Phase 1 착수 전)
> **Author**: wonseok-han
> **Date**: 2026-04-13
> **Status**: Draft
> **PRD**: `docs/00-pm/mvp.prd.md`

---

## Executive Summary

| Perspective | Content |
|-------------|---------|
| **Problem** | 한국어 라이트 투자자는 영어·용어·분산된 도구 때문에 "지금 이 종목 분위기"를 3분 안에 판단하기 어렵다. 증권사 앱은 가격만, 유튜브는 편향·시의성↓, Yahoo/Investing은 영어·복잡. |
| **Solution** | 티커 하나로 한국어 통합 대시보드(차트+지표 해설+뉴스 요약+AI 시그널+시장 맥락)를 제공하는 무료 **참고 도구**. RAG 기반(서버가 컨텍스트 조립 → Gemini가 판단) 으로 할루시네이션 최소화. 자본시장법 "대가" 요건 회피 위해 **무료 유지**. |
| **Function/UX Effect** | 검색 → 종목 상세 평균 체류 60s+, 검색→상세 전환율 70%+, MAU 1K(3개월)/10K(12개월). 월 운영비 $15 이하. LLM 캐시 hit ratio 70%+. |
| **Core Value** | "미국 주식을 한국어로 3분 안에 이해한다." 영어·용어·분산 세 장벽을 한 화면에 해소. **증권사 앱과 경쟁 아닌 보완** — 우리 앱에서 이해, 증권사 앱에서 매매. |

---

## 1. Overview

### 1.1 Purpose

해외주식 1년 미만의 한국어 라이트 투자자가 **종목 의사결정 직전** 가장 먼저 여는 한국어 분석 도구를 만든다. MVP 종료 시점(v1.0)에 다음 3가지가 성립해야 한다.

1. **단일 종목 대시보드**: 티커 하나로 차트·지표·뉴스·AI 시그널이 한 화면에 조립됨
2. **시장 맥락 대시보드**: "오늘 시장 어때?" 질문에 한국어로 답이 되는 랜딩
3. **법적 안전성**: 유사투자자문업 미신고로 합법 운영(무료 + "분석 도구" 포지셔닝 + 면책 UX 전면)

### 1.2 Background

상세 배경은 `docs/00-pm/mvp.prd.md` (Section 5.2) 참조. 3줄 요약:
- LLM 비용 급락 + 차트 라이브러리 오픈소스화 + 국내 해외주식 붐 2~3년차 "도구 공백"이 2024~2025년에 수렴
- 1인 개발자가 월 $15 이하로 한국어 미국 주식 분석 서비스를 지속 가능하게 운영 가능해짐
- 무료 유지로 **자본시장법 대가 요건 회피 → 유사투자자문업 신고 불필요 (A안)**

### 1.3 Related Documents

- **PRD**: `docs/00-pm/mvp.prd.md` (모든 섹션 참조)
- **기획 고정본**: `docs/planning/01-overview.md` ~ `07-legal-compliance.md`
- **프로젝트 컨벤션**: `CLAUDE.md`
- **차후 Design 문서**: `docs/02-design/features/mvp.design.md` (미작성)

---

## 2. Scope

### 2.1 In Scope (MVP = v0.1 ~ v1.0)

- [ ] **F1 종목 검색 + 기본정보** — Finnhub `/search` + `/profile2`, 자동완성 + 최근 검색
- [ ] **F2 차트** — TradingView Lightweight Charts, 캔들 + MA(5/20/60) + 볼린저밴드 + 거래량, 시간프레임 6종 (1D/1W/1M/3M/1Y/5Y)
- [ ] **F3 기술 지표 카드** — ta4j 서버 계산: RSI / MACD / Bollinger / MA + 초보 해설 툴팁
- [ ] **F4 AI 시그널** — Gemini 1.5 Flash, 서버-측 컨텍스트 조립(지표+뉴스+시장) → JSON (`signal` · `confidence` · `rationale` · `risks` · `summary_ko`)
- [ ] **F5 종목/시장 뉴스** — Finnhub `/company-news` + Gemini 한국어 요약 (제목/3줄 요약/원문 링크)
- [ ] **F6 시장 대시보드** — 주요 지수(SPX/NDX/DJI) · VIX · USD/KRW · 10Y 금리 · 시장 뉴스 · 인기/급등락 TOP
- [ ] **법적·운영 필수물** — 면책 고지(전 페이지 푸터 + 종목 상세 상단 배너) · 이용약관 · 프라이버시 정책 · 금지용어 CI 검사
- [ ] **인프라 기본기** — Redis 캐시 (Upstash) · 에러 모니터링 · 구조화 로그 · 모바일 반응형 · SEO (인기 10종목 SSR)
- [ ] **F9 용어사전(여유 시)** — RSI/MACD/볼밴 정적 설명 페이지

### 2.2 Out of Scope (MVP 이후 = v1.1+)

- **F7 북마크** — Supabase Auth + Spring JWT (v1.1 / Phase 4)
- **F8 푸시 알림** — Web Push API + 스케줄러 (v1.1 / Phase 4)
- **F10 시장 일일 브리핑** — AI 자동 생성 일일 요약 (v1.2+)
- **네이티브 모바일 앱** — React Native/Flutter (v1.2+)
- **추가 지표** — 스토캐스틱 / 피보나치 / 일목균형표 (사용자 피드백 기반)
- **유료 플랜** — 자본시장법 재검토 후에만 검토 (Phase 5 이후)
- **커뮤니티/댓글** — 유사투자자문업 경계 모호해짐 → 장기간 불가
- **실시간 체결/매매 연동** — 증권사 API 연동 (MVP 철학에 반함)

---

## 3. Requirements

### 3.1 Functional Requirements

| ID | Requirement | Priority | Status |
|----|-------------|----------|--------|
| FR-01 | 티커/회사명으로 검색 → 자동완성 결과 300ms 내 반환 (Finnhub + Redis 캐시) | High | Pending |
| FR-02 | 종목 상세 페이지 진입 시 차트·지표·AI 시그널·뉴스 단일 화면에 조립 (LCP ≤ 2.5s) | High | Pending |
| FR-03 | TradingView Lightweight Charts로 캔들 + MA5/20/60 + 볼린저밴드 + 거래량 표시, 6개 시간프레임 전환 | High | Pending |
| FR-04 | ta4j 서버 계산: RSI(14) · MACD(12,26,9) · Bollinger(20,2) · MA(5/20/60) → 각 지표에 초보용 한국어 툴팁 | High | Pending |
| FR-05 | Gemini 1.5 Flash 호출로 AI 시그널 JSON 생성: `signal` ∈ {bullish,neutral,bearish}, `confidence` 0~1, `rationale` (근거 배열), `risks` (리스크 배열), `summary_ko` (한국어 3~5문장) | High | Pending |
| FR-06 | 종목/시장 뉴스 Finnhub 수집 → Gemini로 한국어 제목/3줄 요약 + 원문 링크. 원문 전문 재게재 금지 | High | Pending |
| FR-07 | 시장 대시보드: 지수(SPX/NDX/DJI) · VIX · USD/KRW · 10Y금리 · 시장 뉴스 · 인기/급등락 종목 TOP 10 | High | Pending |
| FR-08 | 전 페이지 푸터 + 종목 상세 상단 배너에 "투자 자문 아님 / 투자 판단과 책임은 본인" 면책 고지 | High | Pending |
| FR-09 | 금지 용어 grep CI 검사 (코드·LLM 프롬프트·정적 카피 전수) — "매수 권유" / "매도 권유" / "보장" / "확실" 등 | High | Pending |
| FR-10 | LLM 응답에 금지 용어 포함 시 재시도 또는 안전 응답으로 대체 | High | Pending |
| FR-11 | Redis 캐시 키별 TTL: 시세 30s · 지표 5m · 뉴스 10m · AI 시그널 30m · 프로파일 24h | Medium | Pending |
| FR-12 | 모바일 반응형 (375px~) + 데스크톱 1200px+ 동시 대응 | High | Pending |
| FR-13 | SEO: 인기 10종목(AAPL/TSLA/NVDA/MSFT/GOOGL/META/AMZN/AMD/PLTR/TSM) SSR + Open Graph | Medium | Pending |
| FR-14 | F9 용어사전 정적 페이지 (RSI/MACD/Bollinger/MA/VIX/베타 등) | Low | Pending |
| FR-15 | 이용약관 / 프라이버시 정책 / 면책 상세 페이지 + 푸터 링크 | High | Pending |

### 3.2 Non-Functional Requirements

| Category | Criteria | Measurement Method |
|----------|----------|-------------------|
| **Performance (FE)** | LCP ≤ 2.5s (종목 상세, 모바일 4G) / TTI ≤ 3.5s | Vercel Analytics + Lighthouse CI |
| **Performance (BE)** | `/quote` P95 ≤ 300ms (캐시 hit) / 1.5s (캐시 miss) / AI 시그널 P95 ≤ 6s (최초) / 50ms (캐시) | Spring Actuator + 로그 기반 집계 |
| **Availability** | 월 가동률 99% 이상 (Fly.io/Oracle Cloud Free tier 한계 인지) | UptimeRobot 5분 간격 ping |
| **Cost** | 월 총 운영비 ≤ $15 (MAU 1K 기준), ≤ $30 (MAU 10K 기준) | Finnhub/Gemini/Upstash/Vercel/Fly 대시보드 월 집계 |
| **LLM Cache Hit** | ≥ 70% (Redis AI 시그널 30분 TTL 기준) | 캐시 hit/miss 카운터 + Grafana/자체 로그 |
| **Security** | OWASP Top 10 주요 항목 회피 (SQLi/XSS/인증/CSRF/비밀관리) / Spring Security + JWT Resource Server 검증 | 코드 리뷰 + Dependabot + 수동 pentest 1회 |
| **Accessibility** | WCAG 2.1 AA 주요 항목 (색 대비 · 키보드 포커스 · alt 텍스트 · 랜드마크) | Lighthouse Accessibility ≥ 90 |
| **Legal** | 면책 고지 100% 페이지 커버리지 · 금지 용어 CI fail count = 0 · LLM 응답 "투자 자문" 검출 = 0 | Playwright E2E + grep CI + 런타임 가드 로그 |
| **i18n** | 초기: 한국어 전용 UI (영어 뉴스 원문 링크는 영어 유지). 추후 i18n 준비 구조만 | 구조 리뷰 |
| **Observability** | 구조화 로그 (JSON) · 주요 이벤트(search/detail/ai_call/cache_miss) 추적 · 에러 1% 이상 시 알림 | Fly.io 로그 + 자체 Slack webhook (선택) |

### 3.3 Legal / Compliance Requirements (프로젝트 고유)

| ID | Requirement | 근거 |
|----|-------------|------|
| LR-01 | "투자 자문이 아님" 문구가 전 페이지 푸터 + 종목 상세 상단에 상시 노출 | `docs/planning/07-legal-compliance.md` |
| LR-02 | 매수/매도 권유, 수익 보장 등 금지 용어 **코드·프롬프트·정적 카피·LLM 출력** 4개 레벨 모두 차단 | 자본시장법 제101조 유사투자자문업 |
| LR-03 | AI 시그널 응답은 반드시 `rationale` + `risks` 를 포함 (일방 편향 금지) | 내부 "근거 기반 판단" 규칙 |
| LR-04 | 뉴스 원문 전문 재게재 금지 — 요약 + 원문 링크만 허용 | 저작권법 + 크롤링 예의 |
| LR-05 | 무료 유지 (대가 요건 회피). 유료화 시 재검토 필수 | 자본시장법상 "대가" 해석 |
| LR-06 | 런칭 전(v1.0 Public) 변호사 30분 리뷰 통과 | PRD Risk #2 (Riskiest Assumption) |

---

## 4. Success Criteria

### 4.1 Definition of Done (v1.0 MVP Public)

- [ ] F1 ~ F6 기능 요구사항 (FR-01 ~ FR-13) 전부 구현 + 수동 QA 통과
- [ ] 면책 / 이용약관 / 프라이버시 정책 페이지 공개 (FR-15)
- [ ] 금지 용어 CI 검사 통과 (FR-09, LR-02)
- [ ] 변호사 30분 리뷰 통과 (LR-06)
- [ ] Playwright E2E: 검색 → 상세 → AI 시그널 → 뉴스 happy path 통과
- [ ] Lighthouse Performance ≥ 85 (모바일) / ≥ 90 (데스크톱) on 인기 10종목
- [ ] 10종목 수동 파일럿 (AAPL/TSLA/NVDA/MSFT/GOOGL/META/AMZN/AMD/PLTR/TSM) 품질 체크
- [ ] Public Vercel + Fly.io 배포 + 도메인 연결
- [ ] 커뮤니티 공개 포스트 1건 이상 (긱뉴스 or 디스콰이엇 or HN KR)

### 4.2 Quality Criteria

- [ ] FE/BE 각 단위 테스트 핵심 경로 커버리지 ≥ 60% (MVP 기준 실용적 수치, 80%는 v1.1+ 목표)
- [ ] FE lint 0 errors / 0 warnings (ESLint + Prettier + TS strict)
- [ ] BE Checkstyle + Spotless 0 violations
- [ ] Next.js production build 성공 (`pnpm -F web build`)
- [ ] `./gradlew :api:build` 성공 + Flyway 마이그레이션 적용
- [ ] Dependabot security alert 0 high/critical

### 4.3 Launch Metric Gates (v1.0 배포 후 90일)

| Metric | Target | 미달 시 조치 |
|--------|:---:|------|
| MAU | ≥ 1,000 | 유입 채널 재검토 (블로그 SEO 강화) |
| 검색→상세 전환율 | ≥ 70% | 검색 UX / 자동완성 개선 |
| 종목 상세 30s+ 체류율 | ≥ 40% | AI 시그널 가독성 / 지표 카드 재배치 |
| 재방문율 (W4) | ≥ 20% | 즐겨찾기(F7) 우선 투입 재검토 |
| 월 운영비 | ≤ $15 | 캐시 TTL 연장 / 무료 티어 한계 분석 |
| LLM 캐시 hit ratio | ≥ 70% | 종목 집중도 분석, 인기 종목 pre-warm 검토 |

---

## 5. Risks and Mitigation

PRD Section 5.7의 10개 가정 중 **Plan 단계에서 능동적으로 관리할 최상위 5개**:

| # | Risk | Impact | Likelihood | Mitigation |
|---|------|:------:|:----------:|------------|
| R1 | **법적 해석 변동** — 금감원이 "AI 시그널"을 유사투자자문업으로 간주 | High | Low | v1.0 이전 변호사 30분 리뷰 필수 / "분석 도구" 포지셔닝 전면 / 금지용어 CI / Kill switch 준비 |
| R2 | **LLM 품질 불안정** — Gemini Flash 한국어가 초보에게 설득력 부족하거나 할루시네이션 | High | Med | 10명 파일럿 만족도 ≥ 4.0 / 서버 측 RAG로 컨텍스트 강제 주입 / `rationale`·`risks` 필수 필드 / GPT-4o-mini 블라인드 A/B |
| R3 | **외부 API 제한** — Finnhub 60 req/min, Yahoo 비공식 차단 | Med | Med | Redis 캐시 hit ratio ≥ 70% / 인기 종목 pre-warm / Alpha Vantage 2차 fallback (뉴스) / UA/rate limit 준수 |
| R4 | **월 $15 초과 비용** — MAU 성장 시 LLM 토큰 비용 폭증 | Med | Med | AI 시그널 30분 캐시 / 종목 기준 캐시 키 설계 / 사용자 1명당 호출 상한 / 월별 예산 알림 |
| R5 | **면책 UX 실패** — 사용자가 시그널을 "확정 권고"로 오독 (특히 Persona 3) | Med | Med | 면책 배너 A/B 테스트 / `confidence` 숫자 대신 낮음/중간/높음 라벨 / `rationale`·`risks` 동시 노출 강제 |

**상시 회피 원칙:**
- "예측/권유/보장" 단어를 **코드·프롬프트·UI 카피**에서 전면 금지 (grep CI)
- AI 시그널은 "판단"이 아닌 **"지표 요약 + 리스크 나열"** 로 포지셔닝
- 어떤 형태의 대가(광고 수익 포함) 도입 전 변호사 재리뷰

---

## 6. Architecture Considerations

### 6.1 Project Level Selection

| Level | Characteristics | Recommended For | Selected |
|-------|-----------------|-----------------|:--------:|
| **Starter** | Simple structure (`components/`, `lib/`, `types/`) | 정적 사이트, 포트폴리오 | ☐ |
| **Dynamic** | Feature-based modules, BaaS integration 가능 | Web apps with backend, SaaS MVPs, fullstack | ☑ |
| **Enterprise** | Strict layer separation, DI, microservices | 대규모 트래픽 / 복잡한 도메인 | ☐ |

**선택 근거:** Next.js(FE) + Spring Boot(BE) + PostgreSQL + Redis + 외부 API 연동 = 전형적 Dynamic. Enterprise는 1인 개발자에게 과잉, Starter는 BE 필요성 미충족. (`bkit.config.json` 에 `"level": "Dynamic"` 명시됨.)

### 6.2 Key Architectural Decisions

`CLAUDE.md` Tech Stack 고정 결정을 승계하되, Plan 단계에서 **근거**를 명시:

| Decision | Options | Selected | Rationale |
|----------|---------|----------|-----------|
| **FE Framework** | Next.js 14+ / Remix / Nuxt | **Next.js 14+ App Router** | SSR/SSG 혼합(SEO 10종목 랜딩), Vercel 1-click 배포, AI 도구 지원 최상 |
| **FE State** | React Query / SWR / Zustand / Redux | **React Query(서버) + Zustand(클라)** | 서버 상태 캐싱/재검증 자동화, 클라 전역 상태는 경량 Zustand로 분리 |
| **Chart** | TradingView Lightweight / Recharts / Chart.js | **TradingView Lightweight** | 금융 특화 UX, Apache 2.0 무료, 성능 우수 |
| **BE Framework** | Spring Boot 3 / Quarkus / Fastify | **Spring Boot 3.2+ / Java 21** | 가상 스레드로 I/O 병렬, JPA 생태계, ta4j Java 네이티브 |
| **ORM / Migration** | JPA+Hibernate/Flyway / jOOQ / MyBatis | **JPA + Hibernate + Flyway** | 관리형 마이그레이션, 표준 |
| **DB** | PostgreSQL(Supabase) / RDS / Neon | **Supabase PostgreSQL** | 무료 티어 + Auth 통합 + 관리 편의 |
| **Cache** | Upstash Redis / Redis Cloud / Memcached | **Upstash Redis** | HTTP 기반 서버리스, 무료 티어 충분, TTL/키 설계 간단 |
| **Auth** | Supabase Auth + JWT Resource Server / NextAuth 단독 / 자체 구현 | **Supabase Auth(발급) + Spring JWT(검증)** | MVP에서 Auth는 Phase 4로 이연되지만 구조만 예비 |
| **AI** | Gemini 1.5 Flash / GPT-4o-mini / Claude Haiku | **Gemini 1.5 Flash (RAG)** | 무료 티어 + 가격 최저, 한국어 품질 초보 눈높이 적합 |
| **Tech Indicators** | ta4j(Java) / TA-Lib / pandas-ta | **ta4j** | Spring 생태계 일체, Kotlin DSL 빌드 호환 |
| **Deploy (FE)** | Vercel / Cloudflare Pages / Netlify | **Vercel** | Next.js 1등 시민, Preview/Edge, Root Directory `apps/web` |
| **Deploy (BE)** | Fly.io / Oracle Cloud ARM Free / Railway | **Fly.io 우선 / Oracle Cloud 대체안** | 컨테이너·글로벌 edge, Free tier 가능. `apps/api/Dockerfile` |
| **Testing (FE)** | Vitest+Playwright / Jest+Cypress | **Vitest (unit) + Playwright (E2E)** | 속도·ESM 친화, Next.js App Router 친화 |
| **Testing (BE)** | JUnit5 + Testcontainers / Spock | **JUnit5 + Testcontainers (Postgres)** | 실 DB 통합, 마이그레이션 포함 검증 |

### 6.3 Clean Architecture Approach

```
Selected Level: Dynamic (monorepo, no turbo/nx)

Monorepo 레이아웃:
┌─────────────────────────────────────────────────────────────┐
│ ai-stock-advisor/                                           │
│ ├── apps/                                                   │
│ │   ├── web/   (Next.js 14 App Router, TypeScript)          │
│ │   │   ├── src/app/            (라우트: /, /stock/[ticker])│
│ │   │   ├── src/features/       (stock-detail/, market/)    │
│ │   │   ├── src/components/     (공용 UI)                   │
│ │   │   ├── src/lib/            (api client, utils)         │
│ │   │   └── src/types/          (DTO 타입)                  │
│ │   └── api/   (Spring Boot 3, Java 21, Gradle Kotlin DSL)  │
│ │       └── src/main/java/com/aistockadvisor/               │
│ │           ├── stock/      (search, quote, indicators)     │
│ │           ├── market/     (index, vix, rates, trending)   │
│ │           ├── ai/         (Gemini client, prompt, RAG)    │
│ │           ├── news/       (Finnhub news + LLM 요약)       │
│ │           ├── cache/      (Redis 추상화)                  │
│ │           ├── legal/      (면책 상수, 금지어 가드)         │
│ │           └── common/     (error, logging, security)      │
│ ├── docs/           (planning + PDCA 산출물)                │
│ └── .bkit/          (bkit 런타임)                           │
└─────────────────────────────────────────────────────────────┘

레이어 원칙 (BE):
- Controller(얇게) → Service(도메인 로직) → Repository/Client(외부) 
- RAG는 ai/ 패키지 내부에 ContextAssembler → PromptBuilder → GeminiClient → ResponseValidator 체인으로
- LLM 출력 검증(Validator)에 금지용어·JSON 스키마·안전 필드 필수 항목 3중 검사
```

### 6.4 Phase 매핑 (구현 순서)

PRD Section 5.8 Release Plan 과 일치:

| Release | Scope | Timeframe | Gate |
|---------|-------|:---:|------|
| **v0.1 (Phase 1)** | F1/F2/F3/F5(번역 제외) 단일 종목 파이프라인 | Week 1~4 | AAPL 검색 → 차트/지표/원문 뉴스 표시 |
| **v0.2 (Phase 2)** | F4 AI 시그널 + F5 LLM 한국어 번역·요약 | Week 5~7 | 10종목 수동 테스트 품질 OK |
| **v0.3 (Phase 3)** | F6 시장 대시보드 완성 + SEO 랜딩 | Week 8~9 | 메인이 "오늘 시장 어때?"에 답 |
| **v1.0 MVP Public** | 면책/약관/프라이버시 + 변호사 리뷰 + Public 배포 | Week 10 | 법적 체크리스트 100% |

> **권장:** 본 Plan 은 MVP 전체 지도. 실제 실행은 **Phase 단위로 좁힌 서브 Plan** (`phase-1-core`, `phase-2-ai`, `phase-3-market`)으로 쪼개서 PDCA 사이클을 돌리는 것이 효율적임.

---

## 7. Convention Prerequisites

### 7.1 Existing Project Conventions

- [x] `CLAUDE.md` 에 FE/BE 네이밍·파일·폴더·import 순서 정의됨
- [x] `.editorconfig` 존재 (space 2 기본 / Java·Gradle space 4 / md trim 제외)
- [x] `.gitignore` 존재 (Node/Next/Gradle/bkit 런타임/env 제외)
- [x] `.github/PULL_REQUEST_TEMPLATE.md` 존재 (bkit feature + PDCA checklist + 면책 영향도)
- [ ] `docs/01-plan/conventions.md` (Phase 2 Convention 파이프라인 산출물) — 없음
- [ ] `CONVENTIONS.md` project root — 없음 (CLAUDE.md 로 대체)
- [ ] FE ESLint/Prettier 설정 — **Phase 1 `create-next-app` 직후 확정**
- [ ] BE Checkstyle/Spotless 설정 — **Phase 1 Spring Initializr 직후 확정**
- [ ] FE `tsconfig.json` (strict) — **Phase 1 직후**

### 7.2 Conventions to Define/Verify (Phase 1 초기화 시 확정)

| Category | Current State | To Define | Priority |
|----------|---------------|-----------|:--------:|
| **Naming** | CLAUDE.md 에 규칙 있음 | 실제 코드에 ESLint rule 로 강제 | High |
| **Folder structure** | CLAUDE.md 에 지침 있음 | `apps/web/src/features/*` 경계 합의 / `apps/api` 도메인 패키지 확정 | High |
| **Import order** | CLAUDE.md 에 규칙 있음 | ESLint `import/order` + `@/` path alias 설정 | Medium |
| **Environment variables** | CLAUDE.md prefix 규칙 있음 | `.env.example` 파일 / 서버 전용 키 마스킹 로그 | High |
| **Error handling** | 미정의 | BE `@ControllerAdvice` + 통일 에러 DTO / FE toast 공통 핸들러 | Medium |
| **API 계약** | 미정의 | `/api/v1/stocks/{ticker}` 등 URI 스타일 + DTO 이름 (`*Response`/`*Request`) | High |
| **LLM 응답 계약** | 미정의 | `AiSignalResponse` JSON 스키마 고정 + validator | High |
| **로그 계약** | 미정의 | 구조화 JSON + 이벤트 키 (`search` / `detail_view` / `ai_call` / `cache_miss`) | Medium |
| **Git commit** | 미정의 | Conventional Commits + PR 템플릿 준수 | Medium |
| **브랜치 전략** | 결정됨 | Trunk-based + `feat/<bkit-feature>` | — |

### 7.3 Environment Variables Needed

| Variable | Purpose | Scope | To Be Created |
|----------|---------|-------|:-------------:|
| `NEXT_PUBLIC_API_BASE_URL` | FE → BE base URL | Client | ☐ |
| `SUPABASE_URL` | Supabase 프로젝트 URL | Server (+Client 일부) | ☐ |
| `SUPABASE_ANON_KEY` | Supabase 공개 키 | Client | ☐ |
| `SUPABASE_SERVICE_ROLE_KEY` | 서버 전용 관리 키 | Server only | ☐ (Phase 4) |
| `DATABASE_URL` | PostgreSQL 연결 (Supabase) | Server | ☐ |
| `UPSTASH_REDIS_REST_URL` | Redis REST URL | Server | ☐ |
| `UPSTASH_REDIS_REST_TOKEN` | Redis 인증 토큰 | Server | ☐ |
| `FINNHUB_API_KEY` | 시세/뉴스 1차 소스 | Server | ☐ |
| `ALPHAVANTAGE_API_KEY` | 뉴스/지표 보조 소스 | Server | ☐ (여유 시) |
| `GEMINI_API_KEY` | Gemini 1.5 Flash | Server | ☐ |
| `JWT_ISSUER_URI` | Supabase JWT 검증 issuer | Server | ☐ (Phase 4) |
| `APP_ENV` | `local`/`dev`/`prod` | Server | ☐ |

### 7.4 Pipeline Integration

bkit 9-phase Development Pipeline 연계 가능 — MVP 초기 단계에 유용함:

| Phase | Status | Document Location | Command |
|-------|:------:|-------------------|---------|
| Phase 1 (Schema) | ☐ | `docs/01-plan/schema.md` | `/phase-1-schema` |
| Phase 2 (Convention) | ☐ | `docs/01-plan/conventions.md` | `/phase-2-convention` |
| Phase 3 (Scaffold) | ☐ | — | `/phase-3-scaffold` |

**추천 순서 (Plan 승인 후):**
1. Phase 1 Schema — `stocks`, `users`(Phase 4), `bookmarks`(Phase 4) 초기 스키마 정의
2. Phase 2 Convention — ESLint/Prettier/Checkstyle/Spotless 규칙 문서화
3. 그 다음 `/pdca design mvp` 또는 `/pdca design phase-1-core`

---

## 8. Next Steps

1. [ ] **Plan 리뷰 확정** — 이 문서 검토 후 "Draft" → "Approved" 전환
2. [ ] **(선택) 범위 좁히기** — `/pdca plan phase-1-core` 로 v0.1 (Phase 1)만 먼저 세밀 계획 권장
3. [ ] **Design 착수** — `/pdca design mvp` 또는 `/pdca design phase-1-core`
4. [ ] **Phase 1 스캐폴딩** — `apps/web` (`pnpm create next-app`) + `apps/api` (Spring Initializr) 실 초기화
5. [ ] **ENV 템플릿 커밋** — `.env.example` 2종 (`apps/web/.env.example`, `apps/api/src/main/resources/application-example.yml`)
6. [ ] **변호사 30분 리뷰 일정 확보** — v1.0 Public 배포 전 필수 (R1 해소)

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 0.1 | 2026-04-13 | Initial draft (PRD `docs/00-pm/mvp.prd.md` 기반) | wonseok-han |
