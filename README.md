# 지금이니?! (Nowini)

초보 투자자를 위한 AI 기반 미국 주식 참고·분석 웹서비스.

> ⚠️ 본 서비스는 **투자 자문이 아닌** 정보 제공 및 참고용 분석 도구입니다. 모든 투자 판단과 책임은 사용자 본인에게 있습니다.

---

## 주요 기능

- 티커/종목명 입력 → 차트·뉴스·기술적 지표를 종합해 AI 시그널 제공 (매수/중립/매도 참고용)
- TradingView Lightweight Charts 기반 캔들 + 보조지표(MACD / Bollinger / RSI)
- 종목별 최신 뉴스 LLM 번역·요약 (Gemini 2.5 Flash)
- 시장 대시보드: 주요 지수, VIX, USD/KRW, 시장 뉴스
- 계정(Supabase Auth), 북마크, 웹 푸시 알림(뉴스 기반 포함)
- 베타 피드백 채널 (`/feedback`)

## 아키텍처

Monorepo (pnpm workspace / turbo / nx 미도입, 단순 `apps/*` 네이티브 빌드).

```
nowini/
├── apps/
│   ├── web/          Next.js 16 (App Router) + React 19 + Tailwind 4
│   └── api/          Spring Boot 3.5 (Java 21, 가상 스레드)
├── docs/
│   ├── planning/     초기 기획 고정본 (01-overview ~ 07-legal-compliance)
│   ├── 01-plan/      bkit PDCA: 기능별 Plan
│   ├── 02-design/    bkit PDCA: 기능별 Design
│   ├── 03-analysis/  bkit PDCA: Gap Analysis
│   ├── 04-report/    bkit PDCA: 완료 리포트
│   └── archive/      완료된 기능 PDCA 히스토리
├── .github/workflows/
│   ├── ci.yml              Web typecheck/lint/build + API check + Forbidden-terms
│   └── forbidden-terms.yml 4단계 가드 중 Level 4 (투자 자문 표현 방지)
├── Makefile          FE + BE 통합 실행 (cd 래퍼)
└── bkit.config.json  bkit Level: Dynamic
```

### 기술 스택

| Layer | Choice |
|---|---|
| Frontend | Next.js 16 (App Router, TypeScript) + React 19 + Tailwind 4 |
| FE State | React Query (서버) + Zustand (클라) |
| Chart | TradingView Lightweight Charts |
| Backend | Spring Boot 3.5 / Java 21 |
| Build | Gradle (Kotlin DSL) |
| DB | PostgreSQL — Supabase |
| Cache | Redis — Upstash |
| Migration | Flyway |
| Auth | Supabase Auth + Spring Security JWT Resource Server |
| AI | Google Gemini 2.5 Flash (RAG) |
| Tech Indicators | ta4j |
| Deploy (FE) | Vercel |
| Deploy (BE) | Fly.io or Oracle Cloud Free Tier (ARM) |

자세한 배경은 [`docs/planning/03-architecture.md`](docs/planning/03-architecture.md).

## 빠른 시작

### 필수 요건

- Node.js 20+, pnpm 10+
- Java 21 (JDK)
- Docker (로컬 Postgres + Redis 컨테이너)

### 설치

```bash
make install            # FE 의존성 + BE 툴체인 확인
```

### 개발 서버

```bash
make infra-up           # 로컬 Postgres + Redis 컨테이너 기동
cp apps/api/application.example.yml apps/api/.env.local  # 또는 .env.local 에 키 직접 작성
make dev                # FE(:3000) + BE(:8080) 동시 기동
```

개별 실행: `make web-dev`, `make api-dev`. 전체 타깃은 `make help`.

### 검증

```bash
make check              # FE typecheck/lint + BE check (CI 와 동등)
make test               # FE + BE 테스트
```

세부 명령은 각 앱 README 참조:

- [apps/web/README.md](apps/web/README.md) — 프론트엔드
- [apps/api/README.md](apps/api/README.md) — 백엔드

## 환경 변수

| Prefix | Scope | 예시 |
|---|---|---|
| `NEXT_PUBLIC_` | 브라우저 노출 | `NEXT_PUBLIC_API_BASE_URL`, `NEXT_PUBLIC_SUPABASE_URL` |
| `SUPABASE_` | 서버 전용 | `SUPABASE_SERVICE_ROLE_KEY` |
| `GEMINI_` | 서버 전용 | `GEMINI_API_KEY` |
| `REDIS_` / `UPSTASH_` | 서버 전용 | `UPSTASH_REDIS_REST_URL` |
| `FINNHUB_` / `ALPHAVANTAGE_` / `TWELVE_DATA_` / `FMP_` | 서버 전용 | `FINNHUB_API_KEY` |
| `VAPID_` | 서버 전용 | `VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY` |

FE: `apps/web/.env.local`, BE: `apps/api/.env.local` (Makefile `api-dev`가 자동 source).

## 개발 프로세스

- Trunk-based — `main` 보호, `develop` 에서 기능 통합
- 기능 브랜치: `feat/<feature>` → PR (squash merge)
- 릴리스: `develop` → `main` PR (일반 merge)
- PDCA 사이클 (bkit): Plan → Design → Do → Analyze → Report → Archive

자세한 개발 가이드 / 컨벤션은 [`CLAUDE.md`](CLAUDE.md).

## 문서

| 종류 | 경로 |
|---|---|
| 기획 고정본 | [`docs/planning/`](docs/planning/) |
| 진행 중 PDCA | `docs/01-plan/` / `docs/02-design/` / `docs/03-analysis/` / `docs/04-report/` |
| 완료 아카이브 | [`docs/archive/`](docs/archive/) |

## 법적 고지

- 이용약관: `/legal/terms`
- 개인정보처리방침: `/legal/privacy`
- 면책 원칙: [`docs/planning/07-legal-compliance.md`](docs/planning/07-legal-compliance.md)
