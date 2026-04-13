# 03. 시스템 아키텍처 & 기술 스택

## 3.1 아키텍처 개요

```
┌──────────────┐         ┌──────────────────┐        ┌─────────────────┐
│              │  HTTPS  │                  │ HTTPS  │                 │
│  Next.js 14  │────────▶│  Spring Boot 3   │───────▶│  외부 API들     │
│  (Vercel)    │  JWT    │  (Fly.io/Oracle) │        │  Finnhub, Gemini│
│              │◀────────│                  │◀───────│  Yahoo, ...     │
└──────┬───────┘         └────────┬─────────┘        └─────────────────┘
       │                          │
       │                          ├──▶ Postgres (Supabase)
       │                          │    · users
       │                          │    · bookmarks
       │                          │    · notification_settings
       │                          │    · ai_analysis_cache
       │                          │    · news_translation_cache
       │                          │
       │                          └──▶ Redis (Upstash)
       │                               · 시세/지표 캐시
       │                               · rate limit 카운터
       │                               · AI 분석 단기 캐시
       │
       └──▶ Supabase Auth (로그인, JWT 발급)
```

## 3.2 기술 스택

### Frontend — Next.js

| 항목 | 선택 | 이유 |
|---|---|---|
| 프레임워크 | **Next.js 14+ (App Router)** | SSR/ISR, 블로그 경험 재사용 |
| 언어 | TypeScript | 타입 안정성 |
| 스타일 | Tailwind CSS | 빠른 프로토타이핑 |
| 상태관리 | Zustand or React Query | 서버 상태는 React Query, 클라 상태는 Zustand |
| 차트 | TradingView Lightweight Charts | 무료, 상업적 사용 가능 |
| 인증 | Supabase Auth JS SDK | JWT 발급받아 Spring Boot에 전달 |
| 알림 | Web Push API + Service Worker | MVP는 PWA 기반 |
| 배포 | **Vercel** | Next.js 최적화, 무료 티어 |

### Backend — Spring Boot

| 항목 | 선택 | 이유 |
|---|---|---|
| 프레임워크 | **Spring Boot 3.2+** | 사용자 선택, 성숙한 생태계 |
| 언어 | **Java 21** (LTS) | 가상 스레드 활용 가능 |
| 빌드 | Gradle (Kotlin DSL) | 최신 스타일 |
| Web | Spring Web (MVC) | 단순함 우선. WebFlux는 러닝커브↑ |
| DB 접근 | Spring Data JPA + Hibernate | 표준 |
| Cache | Spring Data Redis | Upstash Redis 연동 |
| Auth | Spring Security + JWT Resource Server | Supabase JWT 검증만 수행 |
| 기술지표 | **ta4j** | MACD/볼밴/RSI 계산 라이브러리 |
| HTTP 클라이언트 | WebClient (reactive) or RestClient | 외부 API 호출 |
| 스케줄러 | `@Scheduled` | 알림 체크, 캐시 warmup |
| 마이그레이션 | Flyway | DB 버전 관리 |
| 모니터링 | Spring Actuator + /health | 기본 헬스체크 |
| 배포 | **Fly.io** or **Oracle Cloud Free Tier (ARM)** | 무료 Spring Boot 호스팅 |

### Data Layer

| 항목 | 선택 | 무료 티어 |
|---|---|---|
| DB | PostgreSQL — **Supabase** | 500MB, 2 projects |
| Cache | Redis — **Upstash** | 10K commands/day |
| Auth | Supabase Auth | 50K MAU |

### 외부 서비스

| 용도 | 서비스 | 비용 |
|---|---|---|
| 주식 시세/뉴스 | Finnhub | 무료 (60 req/min) |
| 기술지표 보조 | Alpha Vantage | 무료 (25 req/day) |
| AI 분석 | **Google Gemini 1.5 Flash** | 무료 티어 + 저렴 |
| 뉴스 번역 | Gemini 1.5 Flash (동일) | " |
| 감성분석 (선택) | HuggingFace FinBERT | 무료 추론 API |
| 환율 | exchangerate.host / Yahoo | 무료 |

## 3.3 주요 설계 원칙

### 캐시 퍼스트 (Cache-First)

무료 API rate limit 때문에 **거의 모든 외부 호출은 Redis 캐시를 경유**합니다.

```
Client → Spring Boot → Redis (hit?)
                         │
                         ├─ hit  → 반환
                         └─ miss → 외부 API → Redis 저장 → 반환
```

**캐시 TTL 가이드:**
| 데이터 | TTL |
|---|---|
| 실시간 시세 (15분 지연) | 60초 |
| OHLCV 캔들 (일봉) | 10분 |
| OHLCV 캔들 (분봉) | 1분 |
| 기술적 지표 | 5분 |
| 종목 뉴스 목록 | 15분 |
| 개별 뉴스 번역본 | 24시간 |
| AI 분석 결과 | 1시간 |
| 시장 뉴스 | 15분 |
| 환율/VIX | 5분 |

### 외부 API Failover

Finnhub 장애/한도 초과 시 Yahoo Finance(비공식)로 fallback. Spring Boot에서 **Port/Adapter 패턴**으로 `StockDataProvider` 인터페이스 뒤에 여러 구현체를 두고 장애 시 전환.

### DB 스키마 (초안)

```sql
-- 사용자는 Supabase Auth 관리. Spring Boot는 user_id(UUID)만 보관.

CREATE TABLE bookmarks (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    ticker VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (user_id, ticker)
);

CREATE TABLE notification_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    ticker VARCHAR(16) NOT NULL,
    price_change_threshold NUMERIC,    -- ±%
    on_new_news BOOLEAN DEFAULT false,
    on_signal_change BOOLEAN DEFAULT false,
    enabled BOOLEAN DEFAULT true
);

CREATE TABLE push_subscriptions (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    endpoint TEXT NOT NULL,
    p256dh TEXT NOT NULL,
    auth TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE ai_analysis_cache (
    ticker VARCHAR(16) PRIMARY KEY,
    signal VARCHAR(32),
    confidence NUMERIC,
    payload JSONB,         -- 전체 AI 응답
    generated_at TIMESTAMPTZ DEFAULT NOW(),
    expires_at TIMESTAMPTZ
);

CREATE TABLE news_translation_cache (
    news_id VARCHAR(128) PRIMARY KEY,
    ticker VARCHAR(16),
    title_ko TEXT,
    summary_ko TEXT,
    original_url TEXT,
    published_at TIMESTAMPTZ,
    cached_at TIMESTAMPTZ DEFAULT NOW()
);
```

> `ai_analysis_cache`와 `news_translation_cache`는 Redis가 아닌 Postgres에 저장하는 이유: **장기 보관 + 과거 이력 조회 가능성** 때문. Redis는 hot 캐시용.

## 3.4 배포 구성

### MVP

- **Frontend:** Vercel
- **Backend:** Fly.io 무료 shared-cpu-1x + 256MB (혹은 Oracle Cloud ARM 4vCPU/24GB 무료)
- **DB:** Supabase Free
- **Redis:** Upstash Free
- **도메인:** Cloudflare Registrar (~$10/year)

### CI/CD

- **Frontend:** Vercel 자동 배포 (GitHub 연동)
- **Backend:** GitHub Actions → Docker 이미지 빌드 → Fly.io 배포
- **DB 마이그레이션:** Flyway (Spring Boot 시작 시 자동 실행)

## 3.5 예상 월 비용 (MVP)

| 항목 | 비용 |
|---|---|
| Vercel Free | $0 |
| Supabase Free | $0 |
| Upstash Free | $0 |
| Fly.io Free (또는 Oracle) | $0 |
| Gemini Flash API (월 ~10K 분석 + 번역) | **~$5~10** |
| Finnhub / Alpha Vantage / Yahoo | $0 |
| 도메인 | ~$1 (연 $12 분할) |
| **합계** | **~$6 ~ $11/월** |
