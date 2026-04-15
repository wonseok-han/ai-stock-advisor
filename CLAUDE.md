# AI Stock Advisor

> 초보 투자자를 위한 AI 기반 미국 주식 참고/분석 웹서비스. 투자 자문이 아닌 **정보 제공 및 참고용 분석 도구**.

---

## Core Principles

### 1. Automation First, Commands are Shortcuts

PDCA 방법론은 자동 적용됩니다. `/pdca plan|design|do|analyze|report` 는 파워유저용 단축키입니다.

### 2. SoR (Single Source of Truth) 우선순위

1. **Codebase** — 실제 동작하는 코드 (현재는 없음, Phase 0)
2. **CLAUDE.md** — 이 문서 (프로젝트 규칙/컨벤션)
3. **docs/planning/** — 초기 서비스 기획/사양 고정본 (01-overview ~ 07-legal-compliance)
4. **docs/01-plan/, docs/02-design/, ...** — bkit PDCA 산출물 (기능별 계획/설계/분석/리포트)

### 3. No Guessing

모르는 것 → 문서 확인 → 없으면 사용자에게 질문 → 절대 추측 금지.

### 4. 면책 원칙 (서비스 핵심)

이 서비스는 **투자 자문이 아님**. 모든 UI/API/문구에 "참고용", "투자 판단과 책임은 사용자 본인" 원칙이 일관되게 반영되어야 함. 자세한 내용은 `docs/planning/07-legal-compliance.md`.

---

## Tech Stack

| Layer | Choice |
|---|---|
| **Level** | Dynamic (bkit) |
| **Frontend** | Next.js 16 (App Router, TypeScript, Tailwind 4) + React 19 |
| **FE State** | React Query (서버 상태) + Zustand (클라 상태) |
| **Chart** | TradingView Lightweight Charts |
| **Backend** | Spring Boot 3.5.13 / Java 21 (LTS, 가상 스레드) |
| **Build** | Gradle (Kotlin DSL) |
| **DB** | PostgreSQL — Supabase |
| **Cache** | Redis — Upstash |
| **ORM** | Spring Data JPA + Hibernate |
| **Migration** | Flyway |
| **Auth** | Supabase Auth (발급) + Spring Security JWT Resource Server (검증) |
| **AI** | Google Gemini 2.5 Flash (RAG) |
| **Tech Indicators** | ta4j (MACD / Bollinger / RSI) |
| **Deploy (FE)** | Vercel |
| **Deploy (BE)** | Fly.io or Oracle Cloud Free Tier (ARM) |

> Tier 1 (TypeScript) + Tier 2 (Java) 혼합 — 풀 AI 지원은 FE 쪽이 더 강하고, BE는 규모가 커질 경우 AI 협업 밀도가 낮아질 수 있음을 염두.

---

## Current Status

**Phase 0 — 기획 진행 중.** 코드 없음. 다음 단계:

- [ ] MVP 기능 명세 확정 (`docs/planning/02-features.md` 리뷰 후)
- [ ] DB 스키마 최종 확정
- [ ] 와이어프레임 / 화면 설계 초안
- [ ] 법적 고지 문구 확정

완료 조건: "개발에 들어가도 되겠다" 확신. 이후 Phase 1 (단일 종목 데이터 파이프라인)로 진입.

전체 로드맵: `docs/planning/06-roadmap.md`.

---

## Development Workflow

### Commands

루트의 `Makefile`을 통해 BE/FE 명령을 통합 실행합니다. (pnpm workspace / turbo / nx 도입 안 함 정책에 따라 단순 cd 래퍼.)

```bash
make help            # 사용 가능한 모든 타깃
make install         # FE pnpm install + BE 툴체인 확인

# Dev
make dev             # FE + BE 동시 기동 (Ctrl+C로 둘 다 종료)
make web-dev         # FE만
make api-dev         # BE만 (./gradlew bootRun)

# Build
make build           # FE + BE
make web-build       # pnpm build
make api-build       # ./gradlew build -x test

# Check (CI 동등)
make check           # FE typecheck/lint + BE check
make web-check       # tsc --noEmit + pnpm lint
make api-check       # ./gradlew check (테스트 + 정적 분석)

# Test / Lint / Clean
make test | make lint | make clean
```

> 개별 명령을 직접 실행하려면 `cd apps/web && pnpm <cmd>` 또는 `cd apps/api && ./gradlew <task>` 도 가능. CI(`.github/workflows/ci.yml`)는 Make 의존을 피해 개별 명령을 직접 호출합니다.

---

## Coding Conventions

### Frontend (TypeScript / Next.js)

| Target | Convention | Example |
|---|---|---|
| Components | PascalCase | `StockChart`, `NewsCard` |
| Functions | camelCase | `fetchQuote`, `formatTicker` |
| Constants | UPPER_SNAKE_CASE | `DEFAULT_CACHE_TTL`, `API_BASE_URL` |
| Types/Interfaces | PascalCase | `StockQuote`, `AiAnalysis` |
| Files (all) | `kebab-case.{ts,tsx}` | `stock-chart.tsx`, `format-date.ts`, `use-stock-detail.ts` |
| Folders | `kebab-case` | `stock-detail/`, `market-dashboard/` |

> **파일명 규칙**: FE의 모든 파일명은 `kebab-case`로 통일합니다. 컴포넌트 식별자(export 이름)는 `PascalCase`, 함수/훅은 `camelCase`를 유지하되, **파일명만 케밥케이스**로 작성합니다. 예: `StockChart` 컴포넌트 → `stock-chart.tsx`, `useStockDetail` 훅 → `use-stock-detail.ts`. Next.js 예약 파일(`page.tsx`, `layout.tsx`, `loading.tsx`, `error.tsx`, `route.ts`, `sitemap.ts`, `robots.ts` 등)은 프레임워크 규칙을 따릅니다.

Import order: (1) 외부 라이브러리 → (2) `@/...` 절대경로 → (3) 상대경로 → (4) `import type { ... }` → (5) 스타일.

### Backend (Java / Spring Boot)

| Target | Convention | Example |
|---|---|---|
| Classes | PascalCase | `StockController`, `FinnhubClient` |
| Methods | camelCase | `getQuote`, `fetchCandles` |
| Constants | UPPER_SNAKE_CASE | `DEFAULT_CACHE_TTL` |
| Packages | lowercase.dot | `com.aistockadvisor.stock` |
| DTOs | `*Request` / `*Response` | `QuoteResponse` |

패키지 레이아웃: domain-driven (`stock/`, `market/`, `ai/`, `auth/`, `bookmark/`, `notification/`).

### 환경 변수 규칙

| Prefix | Scope | Example | Note |
|---|---|---|---|
| `NEXT_PUBLIC_` | 브라우저 노출 | `NEXT_PUBLIC_API_BASE_URL` | 공개 가능한 값만 |
| `SUPABASE_` | 서버 전용 | `SUPABASE_SERVICE_ROLE_KEY` | 절대 노출 금지 |
| `GEMINI_` | 서버 전용 | `GEMINI_API_KEY` | LLM 키 |
| `REDIS_` / `UPSTASH_` | 서버 전용 | `UPSTASH_REDIS_REST_URL` | — |
| `FINNHUB_` / `ALPHAVANTAGE_` | 서버 전용 | `FINNHUB_API_KEY` | 외부 시세 API 키 |

---

## Project Structure (계획)

```
ai-stock-advisor/
├── apps/
│   ├── web/               # Next.js 16 (FE) — Phase 1에서 생성
│   └── api/               # Spring Boot 3 (BE) — Phase 1에서 생성
├── docs/
│   ├── planning/          # 초기 기획 고정본 (01 ~ 07)
│   ├── 01-plan/           # bkit PDCA: 기능별 Plan 문서
│   ├── 02-design/         # bkit PDCA: 기능별 Design 문서
│   ├── 03-analysis/       # bkit PDCA: Gap Analysis
│   ├── 04-report/         # bkit PDCA: 완료 리포트
│   └── archive/           # bkit PDCA: 완료/아카이브
├── .bkit/                 # bkit 런타임 상태 (state / runtime / snapshots)
├── bkit.config.json       # bkit 프로젝트 설정 (Level: Dynamic)
├── CLAUDE.md              # (이 문서)
└── README.md
```

> **구조 확정 (2026-04-10):**
> - **Monorepo** — 단일 repo, `apps/web` + `apps/api` 네이티브 빌드 (pnpm workspace / turbo / nx **도입 안 함**)
> - **형상관리:** GitHub **Public** repo + **Trunk-based** (main 보호) + feature 브랜치 (`feat/<bkit-feature>`)
> - **개발 형태:** 1인 개발
> - **Vercel** Root Directory = `apps/web`, **Fly.io / Oracle Cloud** = `apps/api/Dockerfile`
> - `apps/web`, `apps/api` 실제 초기화(`create-next-app`, Spring Initializr)는 **Phase 1 시작 시점**에 수행

---

## PDCA Auto Behavior (bkit)

### 신규 기능 요청 시

```
사용자: "종목 검색 API 만들어줘"
Claude:
  1. docs/02-design/features/stock-search.design.md 확인
  2. 없으면 bkit-templates 로 design 문서 생성
  3. 설계 기반 구현 (apps/api + apps/web)
  4. 완료 후 gap 분석(/pdca analyze stock-search) 제안
```

### 버그 수정 / 리팩터링 시

```
Claude:
  1. 코드 ↔ design 문서 비교
  2. 원인 파악 후 수정
  3. 설계 문서 업데이트 필요 여부 점검
```

---

## Key Commands (bkit)

| 명령 | 설명 |
|---|---|
| `/pdca status` | 현재 PDCA 상태 |
| `/pdca pm {feature}` | PM Agent Team으로 사전 제품 분석 (권장: Phase 0 → Phase 1 전환기에 유용) |
| `/pdca plan {feature}` | 기능 플랜 작성 |
| `/pdca design {feature}` | 기능 설계 문서 작성 |
| `/pdca do {feature}` | 구현 가이드 |
| `/pdca analyze {feature}` | 설계 vs 구현 Gap 분석 |
| `/pdca report {feature}` | 완료 리포트 생성 |
| `/development-pipeline status` | 9단계 파이프라인 현재 위치 |
| `/code-review <path>` | 코드 리뷰 |
| `/zero-script-qa` | 로그 기반 QA |

---

## 문서 구조 규칙

### docs/planning/ (초기 기획 고정본)
- 서비스 포지셔닝, 아키텍처, 데이터 소스, AI 전략, 로드맵, 법적 고지
- **수정은 신중하게** — 주요 결정 변경 시에만 업데이트
- 기능별 상세 설계는 이쪽이 아님 → `docs/02-design/`

### docs/01-plan/ ~ 04-report/ (bkit PDCA)
- 기능 단위 Plan → Design → Do → Analyze → Report 사이클
- 완료되면 `docs/archive/{date}/{feature}` 로 이동

### docs/archive/ (히스토리)
- 완료된 기능 PDCA 문서들
- 읽기 전용 (수정 금지)

### 아카이브 트리거
- Gap analysis 매칭률 ≥ 90% 달성 OR 사용자 명시적 완료 선언
- → `docs/archive/` 로 이동

---

## 참고

- **기획 문서:** `docs/planning/` (01-overview ~ 07-legal-compliance)
- **bkit 도움말:** `/bkit` 또는 `bkit help`
- **PDCA 템플릿:** `bkit:bkit-templates` 스킬
- **Level 재확인 필요 시:** `BKIT_LEVEL=Dynamic` 환경변수 또는 `bkit.config.json` 의 `level` 필드

---

**Generated for**: AI Stock Advisor
**bkit Version**: 1.6.1
**Level**: Dynamic
**Phase**: 0 (Planning)
