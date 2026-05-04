# 지금이니?! (Nowini)

초보 투자자를 위한 AI 기반 미국 주식 참고·분석 웹서비스.

> ⚠️ 본 서비스는 **투자 자문이 아닌** 정보 제공 및 참고용 분석 도구입니다. 모든 투자 판단과 책임은 사용자 본인에게 있습니다.

---

## 주요 기능

- **AI 시그널**: 차트·뉴스·기술 지표를 종합한 단기/장기 이중관점 참고 분석 (매수/중립/매도)
- **AI 타이밍 판정**: "지금이니?!" — 8개 기술 팩터 기반 진입 조건 충족도 시각화 (NOW / UNCERTAIN / NOT_YET)
- **SEC 공시 분석**: EDGAR 8-K/10-K 공시 Gemini 한국어 요약 + AI 컨텍스트 통합
- **차트**: TradingView Lightweight Charts 기반 캔들 + 보조지표 (MACD / Bollinger / RSI)
- **뉴스**: 종목별 최신 뉴스 LLM 번역·요약 (Gemini 2.5 Flash)
- **시장 대시보드**: 주요 지수 (S&P500, Nasdaq, Dow, VIX, 선물), USD/KRW, 섹터 퍼포먼스, 매크로 지표
- **종목 상세**: 기업 정보, 시가총액, P/E, EPS, 52주 고저, 애널리스트 평점·목표가, 분기 실적
- **인증**: Supabase Auth (이메일/비밀번호 + Google OAuth)
- **북마크 & 알림**: 종목별 조건 설정 (가격 변동 %, 뉴스, 시그널) + Web Push
- **피드백**: `/feedback` 페이지 → DB 저장 + Resend 이메일 알림
- **3-테마 시스템**: Light / Dark / Brand (에메랄드)

## 아키텍처

Monorepo — 단일 repo, `apps/web` + `apps/api` 네이티브 빌드 (pnpm workspace / turbo / nx 미도입).

```
nowini/
├── apps/
│   ├── web/               Next.js 16 (App Router) + React 19 + Tailwind 4
│   └── api/               Spring Boot 3.5.13 (Java 21, 가상 스레드)
├── docs/
│   ├── planning/          초기 기획 고정본 (01-overview ~ 07-legal-compliance)
│   ├── 01-plan/           bkit PDCA: 기능별 Plan
│   ├── 02-design/         bkit PDCA: 기능별 Design
│   ├── 03-analysis/       bkit PDCA: Gap Analysis
│   ├── 04-report/         bkit PDCA: 완료 리포트 + Changelog
│   └── archive/           완료된 기능 PDCA 히스토리
├── .github/workflows/
│   ├── ci.yml             Web typecheck/lint/build + API check
│   └── forbidden-terms.yml  투자 자문 표현 방지 가드
├── Makefile               FE + BE 통합 실행 (cd 래퍼)
└── bkit.config.json       bkit Level: Dynamic
```

### 기술 스택

| Layer | Choice |
|---|---|
| Frontend | Next.js 16 (App Router, TypeScript) + React 19 + Tailwind 4 |
| FE State | React Query (서버) + Zustand (클라) |
| Chart | TradingView Lightweight Charts |
| Backend | Spring Boot 3.5.13 / Java 21 (가상 스레드) |
| Build | Gradle (Kotlin DSL) |
| DB | PostgreSQL — Supabase |
| Cache | Redis — Upstash |
| Migration | Flyway |
| Auth | Supabase Auth (발급) + Spring Security JWT Resource Server (검증) |
| AI | Google Gemini 2.5 Flash (RAG) |
| Tech Indicators | ta4j (MACD / Bollinger / RSI) |
| Data Sources | Yahoo Finance (1차) + Finnhub + TwelveData (fallback) + FMP |
| Email | Resend (피드백 알림) |
| Push | Web Push (VAPID) |
| Monitoring | Actuator + Micrometer + Prometheus |
| Deploy (FE) | Vercel |
| Deploy (BE) | Render |

### 데이터 소스 Fallback 체인

| 데이터 | 1차 | 2차 | 3차 |
|---|---|---|---|
| 시세 (Quote) | Finnhub | Yahoo Finance | TwelveData |
| 인트라데이 캔들 | Yahoo Finance (5m) | TwelveData (5min) | — |
| 일봉 (DB-backed) | DB (candles 테이블) | Yahoo Finance (on-demand) | — |
| 뉴스 | Finnhub | — | — |

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
cp apps/api/.env.example apps/api/.env.local   # API 키 설정
cp apps/web/.env.example apps/web/.env.local   # FE 환경변수 설정
make dev                # FE(:3000) + BE(:8080) 동시 기동
```

개별 실행: `make web-dev`, `make api-dev`. 전체 타깃은 `make help`.

### 검증

```bash
make check              # FE typecheck/lint + BE check (CI 동등)
make test               # FE + BE 테스트
```

## 환경 변수

### Frontend (`apps/web/.env.local`)

| 변수 | 설명 |
|---|---|
| `NEXT_PUBLIC_API_BASE_URL` | API 엔드포인트 (기본: `http://localhost:8080/api/v1`) |
| `NEXT_PUBLIC_SITE_URL` | 사이트 URL (기본: `http://localhost:3000`) |
| `NEXT_PUBLIC_SUPABASE_URL` | Supabase 프로젝트 URL |
| `NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY` | Supabase anon key |

### Backend (`apps/api/.env.local`)

| 변수 | 설명 |
|---|---|
| `DATABASE_URL` | PostgreSQL 접속 URL |
| `REDIS_URL` | Redis 접속 URL |
| `FINNHUB_API_KEY` | Finnhub API 키 (시세/뉴스) |
| `TWELVE_DATA_API_KEY` | TwelveData API 키 (fallback 캔들) |
| `FMP_API_KEY` | Financial Modeling Prep API 키 (섹터/movers) |
| `GEMINI_API_KEY` | Google Gemini API 키 (AI 분석) |
| `SUPABASE_URL` | Supabase 프로젝트 URL (JWT 검증) |
| `VAPID_PUBLIC_KEY` / `VAPID_PRIVATE_KEY` | Web Push VAPID 키 |
| `RESEND_API_KEY` | Resend API 키 (피드백 이메일) |
| `CONTACT_EMAIL` | 피드백 수신 이메일 |

전체 목록: [`apps/api/.env.example`](apps/api/.env.example), [`apps/web/.env.example`](apps/web/.env.example)

## 개발 프로세스

### Git 워크플로

```
main (배포) ← develop (통합) ← feat/xxx (작업)
```

- 기능 브랜치: `feat/<feature>` → PR to `develop` (squash merge)
- 릴리즈: `develop` → `main` PR (일반 merge)
- PDCA 사이클 (bkit): Plan → Design → Do → Analyze → Report → Archive

### 릴리즈

- `changelogs/vX.Y.Z.md` 작성 → develop PR → main 머지 → GitHub Actions 자동 태그/릴리즈
- 시맨틱 버전: `v{major}.{minor}.{patch}-beta`
- 현재 버전: **v0.4.0-beta**

## 문서

| 종류 | 경로 |
|---|---|
| 기획 고정본 | [`docs/planning/`](docs/planning/) |
| 진행 중 PDCA | `docs/01-plan/` ~ `docs/04-report/` |
| Changelog | [`changelogs/`](changelogs/) |
| 완료 아카이브 | [`docs/archive/`](docs/archive/) |

## 법적 고지

- 이용약관: `/legal/terms`
- 개인정보처리방침: `/legal/privacy`
- 면책 원칙: [`docs/planning/07-legal-compliance.md`](docs/planning/07-legal-compliance.md)

## License

Proprietary — 지금이니?! (Nowini)
