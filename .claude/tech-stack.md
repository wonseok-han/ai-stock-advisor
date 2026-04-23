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
| **Data Sources** | Yahoo Finance (1차) + Finnhub + TwelveData (fallback) + FMP |
| **Deploy (FE)** | Vercel |
| **Deploy (BE)** | Fly.io or Oracle Cloud Free Tier (ARM) |

> Tier 1 (TypeScript) + Tier 2 (Java) 혼합 — 풀 AI 지원은 FE 쪽이 더 강하고, BE는 규모가 커질 경우 AI 협업 밀도가 낮아질 수 있음을 염두.

### Data Source Fallback 체인

| 데이터 | 1차 | 2차 | 3차 |
|---|---|---|---|
| **시세(Quote)** | Finnhub | Yahoo Finance | TwelveData |
| **인트라데이 캔들** | Yahoo Finance (5m) | TwelveData (5min) | — |
| **일봉 (DB-backed)** | DB (candles 테이블) | Yahoo Finance (on-demand) | — |
| **뉴스** | Finnhub | — | — |

> Yahoo Finance는 API key 불필요 (v8 chart API). TwelveData 무료 플랜은 8 req/min, 800 req/day 제한이므로 최후 fallback으로만 사용.
