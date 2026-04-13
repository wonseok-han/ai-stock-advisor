# mvp Implementation Guide

> **Summary**: v0.1(Phase 1 Single Stock Pipeline) → v0.2(AI+News) → v0.3(Market) → v1.0(Public) 31 스텝 체크리스트. Design 의 §11.2 구현 순서를 Do 단계의 실행 가이드로 전환.
>
> **Project**: AI Stock Advisor
> **Version**: 0.1 (Phase 1 착수)
> **Author**: wonseok-han
> **Date**: 2026-04-13
> **Status**: In Progress
> **Design Doc**: [mvp.design.md](./mvp.design.md)
> **Plan Doc**: [../../01-plan/features/mvp.plan.md](../../01-plan/features/mvp.plan.md)
> **PRD**: [../../00-pm/mvp.prd.md](../../00-pm/mvp.prd.md)

---

## 1. Pre-Implementation Checklist

### 1.1 Documents Verified

- [x] PRD: `docs/00-pm/mvp.prd.md`
- [x] Plan: `docs/01-plan/features/mvp.plan.md`
- [x] Design: `docs/02-design/features/mvp.design.md`
- [x] Conventions: `CLAUDE.md` (프로젝트 컨벤션 섹션 + 환경변수 규칙)
- [x] PR Template: `.github/PULL_REQUEST_TEMPLATE.md`
- [x] Editor Config: `.editorconfig`

### 1.2 Environment Ready (Phase 1 착수 전 준비)

#### 로컬 개발 환경

- [ ] Node.js 20 LTS 설치 (nvm 권장)
- [ ] pnpm 9+ 설치 (`npm install -g pnpm@latest`)
- [ ] Java 21 LTS 설치 (sdkman 또는 brew)
- [ ] Gradle 8.6+ (wrapper 사용하므로 별도 설치 불필요)
- [ ] Docker Desktop (Testcontainers 용)
- [ ] PostgreSQL 15+ 로컬 또는 Supabase 로컬 (docker-compose 준비)

#### 외부 서비스 계정 (프리 티어)

- [ ] **Supabase** 프로젝트 생성 → `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `DATABASE_URL` 확보
- [ ] **Upstash Redis** 데이터베이스 생성 → `UPSTASH_REDIS_REST_URL`, `UPSTASH_REDIS_REST_TOKEN`
- [ ] **Finnhub** 계정 → `FINNHUB_API_KEY` (무료 60 req/min)
- [ ] **AlphaVantage** 계정 (선택) → `ALPHAVANTAGE_API_KEY` (fallback)
- [ ] **Google AI Studio** → `GEMINI_API_KEY` (1.5 Flash, 무료 일 1M 토큰)
- [ ] **Vercel** 계정 (v1.0 배포용, Phase 3 이후)
- [ ] **Fly.io** 계정 (v1.0 BE 배포용, Phase 3 이후)

#### 환경 변수 파일

- [ ] `apps/web/.env.local` 생성 (v0.1 착수 시점에 스캐폴딩과 함께)
- [ ] `apps/api/src/main/resources/application-local.yml` 생성 (.gitignore 대상)
- [ ] `.env.example` (양쪽) 커밋

---

## 2. Implementation Order (31 Steps)

### 2.1 v0.1 · Phase 1 Single Stock Pipeline (Week 1~4)

| # | Task | File/Location | Status |
|:-:|------|---------------|:------:|
| 1 | `apps/api` Spring Initializr 초기화 (Boot 3.2+, Java 21, Gradle Kotlin DSL, deps: Web/JPA/Flyway/Actuator/Validation/Redis) | `apps/api/` | ☐ |
| 2 | `apps/web` `pnpm create next-app@latest` (App Router, TS strict, Tailwind, ESLint) | `apps/web/` | ☐ |
| 3 | `.env.example` 양쪽 + `application.yml`, GitHub Actions skeleton (build + lint + type-check) | `.github/workflows/`, `.env.example`, `application.yml` | ☐ |
| 4 | Flyway `V1__init.sql` (popular_tickers, ai_signal_history, legal_disclaimer_audit) + Supabase 연결 + `popular_tickers` seed (AAPL/TSLA/NVDA/MSFT/GOOGL/META/AMZN/AMD/PLTR/TSM) | `apps/api/src/main/resources/db/migration/V1__init.sql` | ☐ |
| 5 | `FinnhubClient` + `RedisCacheAdapter` + `SearchService` + `QuoteService` + `StockProfile` 조회 API | `apps/api/src/main/java/com/aistockadvisor/stock/` | ☐ |
| 6 | `IndicatorService` (ta4j) + `/indicators` API + 툴팁 JSON | `apps/api/src/main/java/com/aistockadvisor/stock/service/IndicatorService.java` | ☐ |
| 7 | FE `SearchBox` (React Query debounce 300ms) + `/stock/[ticker]` 레이아웃 + `ChartPanel` (TradingView Lightweight) | `apps/web/src/features/search/`, `apps/web/src/app/stock/[ticker]/`, `apps/web/src/features/stock-detail/chart/` | ☐ |
| 8 | FE `IndicatorsPanel` + 툴팁 + `StockHeader` + `TimeFrameTabs` | `apps/web/src/features/stock-detail/indicators/indicators-panel.tsx`, `.../stock-header.tsx`, `.../time-frame-tabs.tsx` | ☐ |
| 9 | `/api/v1/stocks/{ticker}/detail` 통합 엔드포인트 (AI/뉴스 null 허용, virtual threads 병렬) | `apps/api/src/main/java/com/aistockadvisor/stock/web/StockController.java` | ☐ |
| 10 | `DisclaimerBanner` + `DisclaimerFooter` + `/legal/*` 정적 페이지 초안 | `apps/web/src/components/legal/`, `apps/web/src/app/legal/{terms,privacy,disclaimer}/` | ☐ |
| 11 | ✅ **Gate v0.1**: AAPL 검색 → 차트/지표/원문 뉴스(영어 링크) 로컬 표시 | 수동 QA | ☐ |

### 2.2 v0.2 · Phase 2 AI + News 한국어화 (Week 5~7)

| # | Task | File/Location | Status |
|:-:|------|---------------|:------:|
| 12 | `GeminiClient` + `ContextAssembler` + `PromptBuilder` (v1.0 prompt) + `ResponseValidator` (schema+금지어+개수 강제) | `apps/api/src/main/java/com/aistockadvisor/ai/`, `src/main/resources/prompts/ai-signal.v1.0.txt` | ☐ |
| 13 | `AiSignalService` + `/ai-signal` API + `ai_signal_history` INSERT + Redis 30m 캐시 | `apps/api/src/main/java/com/aistockadvisor/ai/service/AiSignalService.java` | ☐ |
| 14 | `NewsService` (Finnhub 수집) + Gemini 한국어 요약 (제목/3줄) + `/news` API + 10m 캐시 | `apps/api/src/main/java/com/aistockadvisor/news/` | ☐ |
| 15 | FE `AiSignalPanel` + `NewsPanel` + `/detail` 통합 렌더 + partial 응답 처리 | `apps/web/src/features/stock-detail/ai-signal/`, `.../news/` | ☐ |
| 16 | `LegalGuardFilter` (Servlet filter) + `forbidden-terms.json` + 런타임 응답 스캔 | `apps/api/src/main/java/com/aistockadvisor/legal/infra/LegalGuardFilter.java`, `src/main/resources/legal/forbidden-terms.json` | ☐ |
| 17 | CI forbidden-terms grep 스크립트 (`.github/workflows/forbidden-terms.yml`) + `apps/`, prompts, static copy 전수 스캔 | `.github/workflows/forbidden-terms.yml` | ☐ |
| 18 | ✅ **Gate v0.2**: 10 종목(AAPL/TSLA/NVDA/MSFT/GOOGL/META/AMZN/AMD/PLTR/TSM) 수동 파일럿 품질 ≥ 4.0/5.0 (수동 스프레드시트 기록) | QA 로그 | ☐ |

### 2.3 v0.3 · Phase 3 Market Dashboard + SEO (Week 8~9)

| # | Task | File/Location | Status |
|:-:|------|---------------|:------:|
| 19 | `MarketDashboardService` + `/market/snapshot` API (indices/VIX/FX/rates/movers/news, 60s TTL) | `apps/api/src/main/java/com/aistockadvisor/market/` | ☐ |
| 20 | FE `/market` 페이지 + `MarketIndicesCard` + `MoversBoard` + `PopularTickersRibbon` | `apps/web/src/app/market/page.tsx`, `apps/web/src/features/market-dashboard/` | ☐ |
| 21 | `/` 랜딩 통합 + 인기 10 종목 ISR (`generateStaticParams` + `revalidate: 3600`) + `sitemap.ts`/`robots.ts` | `apps/web/src/app/page.tsx`, `apps/web/src/app/sitemap.ts`, `apps/web/src/app/robots.ts` | ☐ |
| 22 | F9 용어사전 정적 페이지 (RSI/MACD/Bollinger/MA/VIX/베타) | `apps/web/src/app/glossary/page.tsx` | ☐ |
| 23 | ✅ **Gate v0.3**: `/` 에서 "오늘 시장 어때?" 질문에 답되는지 수동 체크 | 수동 QA | ☐ |

### 2.4 v1.0 · MVP Public (Week 10)

| # | Task | File/Location | Status |
|:-:|------|---------------|:------:|
| 24 | 약관/프라이버시/면책 상세 페이지 최종본 (변호사 검토 반영) | `apps/web/src/app/legal/{terms,privacy,disclaimer}/page.tsx` | ☐ |
| 25 | Playwright E2E 핵심 시나리오 + 면책 커버리지 (`/`, `/stock/AAPL`, `/market`, `/glossary`, `/legal/*`) | `apps/web/e2e/` | ☐ |
| 26 | Lighthouse CI ≥ 85 (mobile) / ≥ 90 (desktop) on 10 종목 랜딩 + home | `.github/workflows/lighthouse.yml` | ☐ |
| 27 | Dependabot + `npm audit` / `./gradlew dependencyCheckAnalyze` CI 게이트 green | `.github/dependabot.yml`, `.github/workflows/security.yml` | ☐ |
| 28 | 변호사 30분 리뷰 통과 (R1 해소) | 외부 일정 | ☐ |
| 29 | Vercel (apps/web) + Fly.io (apps/api) prod 배포 + 도메인 + HSTS 1y | `apps/api/Dockerfile`, `apps/api/fly.toml`, Vercel 설정 | ☐ |
| 30 | 커뮤니티 공개 포스트 1건 이상 (긱뉴스 / 디스콰이엇 / HN KR 중) | 외부 커뮤니티 | ☐ |
| 31 | ✅ **Gate v1.0**: git tag `v1.0.0` + MVP Public 선언 (README 배포 배지 추가) | `git tag v1.0.0 && git push --tags` | ☐ |

---

## 3. Key Files to Create

### 3.1 Backend (apps/api) 신규 파일

| File Path | Purpose |
|-----------|---------|
| `build.gradle.kts` | Spring Boot 3.5.13, Java 21, ta4j, resilience4j, spring-data-redis, springdoc-openapi, flyway (Initializr로 스캐폴딩 후 ta4j/resilience4j/springdoc 추가) |
| `src/main/java/com/aistockadvisor/AiStockAdvisorApplication.java` | 메인 클래스 (`@EnableAsync`, `@EnableCaching`) |
| `src/main/java/com/aistockadvisor/stock/web/StockController.java` | `/search`, `/profile`, `/quote`, `/candles`, `/indicators`, `/detail` |
| `src/main/java/com/aistockadvisor/stock/service/{Search,Quote,Indicator,StockDetail}Service.java` | 도메인 로직 |
| `src/main/java/com/aistockadvisor/stock/infra/client/{Finnhub,AlphaVantage}Client.java` | 외부 클라이언트 어댑터 |
| `src/main/java/com/aistockadvisor/ai/service/rag/{ContextAssembler,PromptBuilder,ResponseValidator}.java` | RAG 파이프라인 |
| `src/main/java/com/aistockadvisor/ai/infra/client/GeminiClient.java` | Gemini REST 어댑터 |
| `src/main/java/com/aistockadvisor/news/service/NewsService.java` | Finnhub 수집 + Gemini 요약 |
| `src/main/java/com/aistockadvisor/market/service/MarketDashboardService.java` | 지수/VIX/FX/movers 통합 |
| `src/main/java/com/aistockadvisor/legal/infra/LegalGuardFilter.java` | Servlet filter · 금지어 전수 스캔 |
| `src/main/java/com/aistockadvisor/cache/RedisCacheAdapter.java` | Redis 캐시 추상화 |
| `src/main/java/com/aistockadvisor/common/error/{GlobalExceptionHandler,ErrorResponse}.java` | @ControllerAdvice 통합 에러 |
| `src/main/java/com/aistockadvisor/common/logging/StructuredLoggingFilter.java` | JSON 구조화 로그 |
| `src/main/java/com/aistockadvisor/common/ratelimit/BucketedRateLimitFilter.java` | IP 기반 버킷 RL |
| `src/main/resources/application.yml` (+ `-local`, `-prod`) | 설정 프로파일 |
| `src/main/resources/db/migration/V1__init.sql` | 초기 스키마 3 테이블 |
| `src/main/resources/prompts/ai-signal.v1.0.txt` | AI 시그널 시스템 프롬프트 (버전 고정) |
| `src/main/resources/legal/forbidden-terms.json` | 금지어 목록 (런타임+CI 공용) |
| `Dockerfile` | `eclipse-temurin:21-jre-alpine` 베이스 · 멀티스테이지 |
| `fly.toml` | Fly.io 앱 정의 |

### 3.2 Frontend (apps/web) 신규 파일

| File Path | Purpose |
|-----------|---------|
| `package.json` | deps: `next@16`, `react@19`, `typescript@5`, `tailwindcss@4`, `@tanstack/react-query@5`, `zustand@4`, `lightweight-charts@4`, devdeps: `eslint@9`, `@typescript-eslint/*`, `prettier`, `@testing-library/react`, `vitest`, `@playwright/test` |
| `tsconfig.json` | strict, `paths.@/*`, `moduleResolution: bundler` |
| `tailwind.config.ts` | content globs, dark mode `class` |
| `next.config.mjs` | `images.remotePatterns` (finnhub/clearbit), strict CSP headers |
| `src/app/layout.tsx` | 전역 Header, `DisclaimerFooter`, QueryClientProvider |
| `src/app/page.tsx` | 랜딩 (시장 요약 + 인기 10 종목) |
| `src/app/stock/[ticker]/page.tsx` | 종목 상세 (ISR for 인기 10, on-demand for 나머지) |
| `src/app/market/page.tsx` | 시장 대시보드 |
| `src/app/glossary/page.tsx` | 용어사전 (F9) |
| `src/app/legal/{terms,privacy,disclaimer}/page.tsx` | 법적 페이지 |
| `src/app/sitemap.ts`, `src/app/robots.ts` | SEO |
| `src/components/layout/{header,footer}.tsx` | 전역 레이아웃 |
| `src/components/legal/{disclaimer-banner,disclaimer-footer}.tsx` | 면책 UI |
| `src/components/ui/{button,card,tabs,tooltip,badge}.tsx` | Headless + Tailwind |
| `src/features/search/search-box.tsx` | 검색 + 자동완성 |
| `src/features/stock-detail/stock-header.tsx` + `time-frame-tabs.tsx` | 상단 |
| `src/features/stock-detail/chart/chart-panel.tsx` | TradingView Lightweight |
| `src/features/stock-detail/indicators/indicators-panel.tsx` | 지표 카드 |
| `src/features/stock-detail/ai-signal/ai-signal-panel.tsx` | 시그널 배지+rationale+risks |
| `src/features/stock-detail/news/news-panel.tsx` | 뉴스 리스트 |
| `src/features/market-dashboard/{market-indices-card,movers-board,popular-tickers-ribbon}.tsx` | 대시보드 컴포넌트 |
| `src/features/*/hooks/use-*.ts` | React Query 훅 (export: `useStockDetail`, `useMarketSnapshot`, `useSearch` / 파일명: `use-stock-detail.ts` 등) |
| `src/lib/api/{stocks,market,legal,client}.ts` | fetch wrapper + API 모듈 |
| `src/lib/format/{currency,percent,date}.ts` | 포맷터 |
| `src/lib/env.ts` | 환경변수 타입 가드 |
| `src/types/{stock,market,common}.ts` | 도메인 타입 |
| `e2e/{search,stock-detail,disclaimer-coverage}.spec.ts` | Playwright E2E |

### 3.3 Repo 레벨 파일

| File Path | Purpose |
|-----------|---------|
| `.github/workflows/ci.yml` | FE typecheck/lint/test/build + BE `./gradlew check build` |
| `.github/workflows/forbidden-terms.yml` | 금지어 grep CI (코드+prompts+static copy 전수) |
| `.github/workflows/lighthouse.yml` | Lighthouse CI (v1.0 직전) |
| `.github/workflows/security.yml` | `npm audit` + `dependencyCheckAnalyze` |
| `.github/dependabot.yml` | FE/BE 각각 주단위 업데이트 |
| `package.json` (루트) | workspaces 없이 단순 scripts만 (`dev:web`, `dev:api`) |

---

## 4. Dependencies

### 4.1 Frontend (pnpm)

```bash
cd apps/web

# Base 스캐폴딩은 `pnpm create next-app@latest`로 이미 설치됨
# (next@16, react@19, react-dom@19, typescript@5, eslint@9, tailwindcss@4, @tailwindcss/postcss@4)

# 추가 런타임 의존성
pnpm add @tanstack/react-query@5 zustand@4 \
  lightweight-charts@4 clsx tailwind-merge

# 추가 개발 의존성 (typescript-eslint는 Next 16 기본 포함이라 생략)
pnpm add -D prettier eslint-config-prettier eslint-plugin-prettier \
  vitest @testing-library/react @testing-library/jest-dom jsdom \
  @playwright/test
```

### 4.2 Backend (Gradle Kotlin DSL `build.gradle.kts`)

```kotlin
dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-cache")

    // DB
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // Tech indicators
    implementation("org.ta4j:ta4j-core:0.17")

    // Resilience
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.2.0")
    implementation("io.github.resilience4j:resilience4j-reactor:2.2.0")

    // HTTP client (BE -> 외부)
    implementation("org.springframework.boot:spring-boot-starter-webflux")  // WebClient

    // JSON
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")    // 선택 (Java 전용이면 제거)

    // API Docs
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0")

    // Security (Phase 4 예비, JWT resource server)
    // implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:junit-jupiter:1.19.8")
    testImplementation("org.testcontainers:postgresql:1.19.8")
    testImplementation("com.github.tomakehurst:wiremock-jre8-standalone:3.0.1")
}

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }
```

### 4.3 로컬 인프라 (Docker Compose, 선택)

```yaml
# docker-compose.yml (개발 편의, 실 배포는 Supabase+Upstash)
services:
  postgres:
    image: postgres:16-alpine
    ports: ["5432:5432"]
    environment:
      POSTGRES_DB: aistockadvisor
      POSTGRES_USER: dev
      POSTGRES_PASSWORD: dev
  redis:
    image: redis:7-alpine
    ports: ["6379:6379"]
```

---

## 5. Implementation Notes

### 5.1 Design Decisions Reference (Design §6.2 승계)

| Decision | Choice | Rationale |
|----------|--------|-----------|
| State (FE) | React Query(서버) + Zustand(UI) | 서버 상태 캐시 자동, 클라 상태는 경량 |
| API Pattern | FE 는 `/api/v1/*` 만 호출 | 키 노출 방지 + 캐시·금지어 가드 단일 지점 |
| 통합 엔드포인트 | `/stocks/{ticker}/detail` 단일 호출 | BE 내부에서 virtual threads 병렬 + partial 응답 |
| Error Handling | `@ControllerAdvice` + partial 패턴 | 일부 블록 실패도 UX 유지 |
| RAG | 서버가 컨텍스트 조립 → LLM 은 판단만 | 할루시네이션·프롬프트 인젝션 방어 |
| 금지어 가드 | 4-level (코드 · 프롬프트 · Validator · Filter) | 법적 R1 상시 차단 |

### 5.2 Code Patterns (핵심만)

**FE: React Query 훅 패턴**
```typescript
// src/features/stock-detail/hooks/use-stock-detail.ts
import { useQuery } from '@tanstack/react-query';
import { stocksApi } from '@/lib/api/stocks';
import type { StockDetail, TimeFrame } from '@/types/stock';

export function useStockDetail(ticker: string, tf: TimeFrame = '1D') {
  return useQuery<StockDetail>({
    queryKey: ['stockDetail', ticker, tf],
    queryFn: () => stocksApi.getDetail(ticker, tf),
    staleTime: 30_000,   // 시세 TTL 과 동기
    enabled: /^[A-Z]{1,5}(\.[A-Z])?$/.test(ticker),
  });
}
```

**BE: Service 오케스트레이션 + virtual threads**
```java
// StockDetailService.java
@Service
public class StockDetailService {
    // ... 의존성 주입

    public StockDetailResponse getDetail(String ticker, TimeFrame tf) {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            var profile  = scope.fork(() -> profileService.get(ticker));
            var quote    = scope.fork(() -> quoteService.get(ticker));
            var candles  = scope.fork(() -> candleService.get(ticker, tf));
            var ind      = scope.fork(() -> indicatorService.compute(ticker));
            var news     = scope.fork(() -> newsService.getKo(ticker));
            var signal   = scope.fork(() -> aiSignalService.get(ticker));
            scope.join(); // 전체 대기 (각 서비스는 내부 타임아웃으로 partial 처리)
            return assembler.assemble(profile.get(), quote.get(), candles.get(),
                                      ind.get(), news.get(), signal.get());
        } catch (Exception e) {
            throw new UpstreamException(e);
        }
    }
}
```

**BE: LLM ResponseValidator 체인**
```java
// ResponseValidator.java
public AiSignal validate(String rawJson) {
    var signal = mapper.readValue(rawJson, AiSignal.class);   // 1. schema
    if (forbiddenTerms.anyMatch(rawJson)) throw new ForbiddenContentException();  // 2. grep
    if (signal.rationale().size() < 3) throw new InsufficientRationaleException();
    if (signal.risks().size() < signal.rationale().size()) throw new InsufficientRisksException();
    return signal;
}
```

### 5.3 Things to Avoid

- [ ] **Hardcoded API keys** — 절대 코드/커밋에 들어가지 않도록 `.env.example` + CI secret 스캔
- [ ] **FE 에서 외부 API 직접 호출** — Finnhub/Gemini 키 노출 + CORS + 캐시 불가
- [ ] **LLM 프롬프트에 사용자 자유 문자열 주입** — 프롬프트 인젝션 → ticker 만 정규식 통과 후 주입
- [ ] **금지어 ("매수 추천" 등) 를 주석/테스트 픽스처에도 하드코딩** — CI grep 에 잡힘 → 항상 변수/JSON 로부터 로딩
- [ ] **`console.log`, `System.out.println` 프로덕션 잔존** — 로그는 모두 구조화 로거
- [ ] **캐시 없는 외부 호출** — 모든 외부 호출은 Redis 경유 필수 (테스트 제외)
- [ ] **`@SuppressWarnings("all")`, `// @ts-ignore`** — 근본 수정
- [ ] **면책 고지 미노출 페이지** — 새 페이지 추가 시 `data-testid="disclaimer"` 요소 필수

### 5.4 Architecture Checklist (Dynamic Level)

- [ ] FE: `src/features/*` 기능 단위 / `src/components/*` 공용 UI / `src/lib/api/*` 인프라 / `src/types/*` 도메인
- [ ] BE: `stock/news/ai/market/legal/cache/common` 도메인 기반 패키지 + 각 패키지 내 `web/service/domain/infra` 4 계층
- [ ] Controller 는 Service 만 의존 (Repository/Client 직접 주입 금지)
- [ ] Domain 계층에 외부 라이브러리 import 금지 (enum / VO / entity 순수)
- [ ] FE 컴포넌트는 fetch 직접 호출 금지 → 훅 → api 모듈 경유

### 5.5 Convention Checklist (CLAUDE.md 승계)

- [x] Components identifier: `PascalCase` / Functions: `camelCase` / Constants: `UPPER_SNAKE_CASE`
- [x] **Files (all): `kebab-case.{ts,tsx}`** (컴포넌트·유틸 구분 없이 파일명은 전부 케밥케이스, Next.js 예약 파일만 예외)
- [x] Folders: `kebab-case`
- [x] Import order: 외부 → `@/` 절대 → 상대 → `import type` → 스타일
- [x] Branches: `feat/<bkit-feature>` (trunk-based)
- [x] Commits: Conventional Commits (`docs(pm):`, `feat(stock-search):`, `fix(ai):`)

### 5.6 Security Checklist

- [ ] Ticker 정규식 화이트리스트 (`^[A-Z]{1,5}(\.[A-Z])?$`)
- [ ] Search query length ≤ 20
- [ ] CSP: `default-src 'self'`, img 소스 화이트리스트
- [ ] Rate limit: `/api/v1/*` 60 req/min/IP, `/ai-signal` 10 req/min/IP
- [ ] CORS: prod 도메인만 (`aistockadvisor.app`) + `localhost:3000`
- [ ] Secrets: Vercel/Fly secrets + GitHub secrets, `.env` 커밋 금지
- [ ] HTTPS + HSTS 1y (prod)
- [ ] 로그에 ticker/requestId/latency/cacheHit 만. IP 는 SHA256 hash
- [ ] Dependabot + `npm audit` / `dependencyCheckAnalyze` CI green

### 5.7 API Checklist

- [ ] 응답 형식 통일: success `{ ...data, meta }` / error `{ error: { code, message, details?, requestId, timestamp } }`
- [ ] 에러 코드 enum (`INVALID_TICKER`, `TICKER_NOT_FOUND`, `UPSTREAM_TIMEOUT`, `LLM_VALIDATION_FAILED`, `FORBIDDEN_CONTENT`, ...)
- [ ] HTTP 메서드: GET 만 (MVP), POST 는 Phase 4 auth/bookmark 까지 미사용
- [ ] Swagger/OpenAPI: springdoc 으로 `/api-docs` 노출 (local only)

---

## 6. Testing Checklist

### 6.1 Manual Testing (Phase 별 Gate)

- [ ] **v0.1 Gate**: AAPL 로컬 검색 → 상세에서 차트/지표/뉴스(영어 원문) 표시
- [ ] **v0.2 Gate**: 10 종목 파일럿 만족도 스프레드시트 평균 ≥ 4.0/5.0
- [ ] **v0.3 Gate**: `/` 에서 "오늘 시장 어때?" 3 초 내 훑기 가능
- [ ] **v1.0 Gate**: 변호사 리뷰 통과 + prod URL 에서 모바일/데스크톱 happy path

### 6.2 Automated Testing

- [ ] FE Vitest: 포맷터 · `useStockDetail` 훅 · 주요 컴포넌트 렌더
- [ ] BE JUnit: Service 로직 · PromptBuilder · ResponseValidator · LegalGuardFilter
- [ ] BE Testcontainers: Controller + Repository + Redis + Flyway 통합
- [ ] WireMock: Finnhub/Gemini 클라이언트 contract
- [ ] Playwright E2E: happy path + 면책 커버리지 5 페이지
- [ ] Lighthouse CI: mobile ≥ 85 / desktop ≥ 90
- [ ] Forbidden-terms grep CI: `apps/**`, `prompts/**`, static copy

### 6.3 Code Quality Gates

- [ ] FE: `pnpm -F web typecheck` 0 에러, `pnpm -F web lint` 0 에러/경고
- [ ] BE: `./gradlew :api:check` 통과 (test + checkstyle/spotless)
- [ ] FE: `pnpm -F web build` 성공
- [ ] BE: `./gradlew :api:build` 성공 + Flyway 마이그레이션 적용
- [ ] Dependabot 0 high/critical

---

## 7. Progress Tracking

### 7.1 Weekly Log (주 단위, 매주 금요일 업데이트)

| Week | Phase | Steps Completed | Notes / Blockers |
|------|-------|-----------------|------------------|
| W1 | v0.1 init | — | 미착수 |
| W2 | v0.1 BE core | — | — |
| W3 | v0.1 FE core | — | — |
| W4 | v0.1 integration | — | — |
| W5 | v0.2 AI | — | — |
| W6 | v0.2 News | — | — |
| W7 | v0.2 Legal Guard | — | — |
| W8 | v0.3 Market | — | — |
| W9 | v0.3 SEO/Glossary | — | — |
| W10 | v1.0 Public | — | 변호사 리뷰 D-7 필수 |

### 7.2 Blockers

| Date | Issue | Impact | Resolution |
|------|-------|--------|------------|
| —    | —     | —      | —          |

---

## 8. Post-Implementation

### 8.1 Self-Review Checklist (v1.0 직전)

- [ ] Design §11.2 의 31 스텝 전부 체크 완료
- [ ] FR-01 ~ FR-15 모두 구현 확인
- [ ] NFR (performance / availability / cost / cache hit / a11y / legal) 측정치 기록
- [ ] LR-01 ~ LR-06 법적 요구사항 체크
- [ ] 변호사 30분 리뷰 로그 (이메일/문서)
- [ ] 금지어 CI green
- [ ] Playwright E2E + Lighthouse CI green
- [ ] 로그 PII 검증 (ticker/requestId/latency/cacheHit 만 노출)

### 8.2 Ready for Check Phase

v1.0 태그 + prod 배포 + 90 일 운영 데이터 수집 후:

```bash
# Gap Analysis 실행
/pdca analyze mvp
```

### 8.3 Post-MVP (v1.1+) 백로그

Design 범위 밖 — 별도 PDCA 사이클로:
- F7 북마크 + Supabase Auth + Spring JWT Resource Server
- F8 Web Push 알림 + 스케줄러
- F10 시장 일일 브리핑 AI 자동 생성
- 추가 지표 (스토캐스틱 · 피보나치)
- 네이티브 모바일 앱 (React Native)

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 0.1 | 2026-04-13 | Initial Do guide. Design §11.2 의 31 스텝을 Week 별 · 파일 별 · 명령 단위로 전환 | wonseok-han |
