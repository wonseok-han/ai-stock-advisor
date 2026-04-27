# apps/web — 지금이니?! Frontend

Next.js 16 (App Router, Turbopack) + React 19 + Tailwind 4 + TypeScript.

> 루트 개요: [`../../README.md`](../../README.md) · 프로젝트 규칙: [`../../CLAUDE.md`](../../CLAUDE.md)

---

## 요구 사항

- Node.js 20+
- pnpm 10+
- Supabase 프로젝트 (Auth + DB) — 베타 키 필요
- 백엔드(`apps/api`) 또는 배포된 API 엔드포인트

## 빠른 시작

```bash
cd apps/web
pnpm install
cp .env.local.example .env.local   # 없으면 아래 '환경 변수' 섹션 참조해 직접 작성
pnpm dev                            # http://localhost:3000
```

루트에서 한 번에: `make web-dev` (또는 `make dev` 로 FE+BE 동시 기동).

## 스크립트

| 명령 | 설명 |
|---|---|
| `pnpm dev` | 개발 서버 (Turbopack) |
| `pnpm build` | 프로덕션 빌드 |
| `pnpm start` | 프로덕션 서버 (build 후) |
| `pnpm lint` | ESLint (next/core-web-vitals + react-hooks) |
| `pnpm exec tsc --noEmit` | 타입 체크 |

## 환경 변수

`.env.local` (Git 무시). **`NEXT_PUBLIC_` 접두사가 붙은 값만 브라우저로 노출**됩니다.

```bash
# API 연결
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080/api/v1

# 사이트 URL
NEXT_PUBLIC_SITE_URL=http://localhost:3000

# Supabase (publishable 키만 브라우저 노출 — service-role 키는 BE 전용)
NEXT_PUBLIC_SUPABASE_URL=https://<project-ref>.supabase.co
NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY=<publishable-key>

# 웹 푸시 (VAPID 공개 키만 브라우저에 필요)
NEXT_PUBLIC_VAPID_PUBLIC_KEY=<base64url-encoded-public-key>
```

## 디렉토리 구조

```
apps/web/
├── public/            정적 자산 (favicon, og-image 등)
├── src/
│   ├── app/           App Router 페이지 + 레이아웃
│   │   ├── (market)/  시장 대시보드
│   │   ├── stock/     종목 상세
│   │   ├── legal/     이용약관·개인정보처리방침
│   │   ├── feedback/  베타 피드백 폼
│   │   └── api/       (필요 시) route handlers
│   ├── components/    재사용 UI (Header, Footer, Legal 등)
│   ├── features/      도메인별 모듈 (stock/, news/, bookmark/, notification/, feedback/, auth/)
│   ├── lib/           Supabase 클라이언트, fetcher, 유틸
│   ├── hooks/         공용 훅 (use-auth 등)
│   └── styles/        globals.css (Tailwind 4)
├── next.config.ts
├── tsconfig.json
└── package.json
```

## 코딩 컨벤션

- **파일명**: `kebab-case.{ts,tsx}` (Next.js 예약 파일 제외: `page.tsx`, `layout.tsx`, `route.ts`, `loading.tsx`, `error.tsx`, `sitemap.ts`, `robots.ts`)
- **컴포넌트 식별자**: `PascalCase` (export 이름)
- **함수/훅**: `camelCase`
- **상수**: `UPPER_SNAKE_CASE`
- **타입/인터페이스**: `PascalCase`
- **import 순서**: 외부 → `@/...` 절대경로 → 상대경로 → `import type` → 스타일

루트 [`CLAUDE.md`](../../CLAUDE.md) 의 Coding Conventions 섹션이 SoR.

## 주의 사항 (Next.js 16)

이 프로젝트는 Next.js 16 — 학습 데이터와 API/컨벤션/디렉토리 구조가 다를 수 있습니다.
새 코드 작성 전 [`node_modules/next/dist/docs/`](./node_modules/next/dist/docs/) 를 먼저 확인하세요.
deprecation 경고는 반드시 반영합니다. 상세는 [`AGENTS.md`](./AGENTS.md).

## 배포

- **플랫폼**: Vercel (Root Directory = `apps/web`, Framework preset = Next.js)
- **브랜치**: `main` push 시 프로덕션 자동 배포, 그 외 브랜치는 프리뷰 배포
- **환경 변수**: Vercel Dashboard → Project → Settings → Environment Variables

## 테스트

현재 FE 단위 테스트는 최소(`pnpm test --if-present` 는 스크립트 없으면 no-op). E2E/단위 테스트는 추후 도입 예정.
