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
| Packages | lowercase.dot | `com.nowini.stock` |
| DTOs | `*Request` / `*Response` | `QuoteResponse` |

패키지 레이아웃: domain-driven (`stock/`, `market/`, `ai/`, `auth/`, `bookmark/`, `notification/`).

### 환경 변수 규칙

| Prefix | Scope | Example | Note |
|---|---|---|---|
| `NEXT_PUBLIC_` | 브라우저 노출 | `NEXT_PUBLIC_API_BASE_URL` | 공개 가능한 값만 |
| `SUPABASE_` | 서버 전용 | `SUPABASE_SERVICE_ROLE_KEY` | 절대 노출 금지 |
| `GEMINI_` | 서버 전용 | `GEMINI_API_KEY` | LLM 키 |
| `REDIS_` / `UPSTASH_` | 서버 전용 | `UPSTASH_REDIS_REST_URL` | — |
| `FINNHUB_` | 서버 전용 | `FINNHUB_API_KEY` | 시세/뉴스 API 키 |
| `TWELVE_DATA_` | 서버 전용 | `TWELVE_DATA_API_KEY` | TwelveData fallback API 키 |
| `FMP_` | 서버 전용 | `FMP_API_KEY` | Financial Modeling Prep API 키 |
