# Auth Planning Document

> **Summary**: Supabase Auth 기반 인증 + 북마크 + Web Push 알림으로 개인화 기능 제공
>
> **Project**: AI Stock Advisor
> **Author**: Claude (AI) + wonseok-han
> **Date**: 2026-04-16
> **Status**: Draft

---

## Executive Summary

| Perspective | Content |
|-------------|---------|
| **Problem** | 현재 서비스는 비로그인 전용이라 관심 종목 저장, 변동 알림 등 개인화가 불가능하다 |
| **Solution** | Supabase Auth(이메일+Google OAuth) 발급 → Spring Security JWT Resource Server 검증 구조로 인증을 추가하고, 북마크·Web Push 알림으로 개인화를 구현한다 |
| **Function/UX Effect** | 로그인 후 종목 북마크 토글, 마이페이지에서 관심 종목 한눈에 확인, 가격 변동·뉴스·시그널 변화 시 브라우저 푸시 알림 수신 |
| **Core Value** | "내가 찜한 종목에 변화가 생기면 알림이 온다" — 수동 모니터링 없이 시장 변화를 놓치지 않는 경험 |

---

## 1. Overview

### 1.1 Purpose

Phase 1~3에서 구축한 시세·차트·AI 분석·대시보드 위에 **사용자 식별(인증)** 을 추가하여, 관심 종목 저장(북마크)과 조건부 알림(Web Push)이라는 개인화 기능을 제공한다.

### 1.2 Background

- 현재 서비스는 비로그인 상태에서 모든 기능을 사용 가능하나, "관심 종목 저장" 과 "변동 알림" 은 사용자 식별이 필수
- Roadmap Phase 4 목표: "내가 찜한 종목에 변화가 생기면 알림이 온다"
- 인증 인프라는 이후 Phase 5+ 기능(포트폴리오 시뮬레이션, 개인화 추천 등)의 기반이 됨

### 1.3 Related Documents

- 기능 명세: `docs/planning/02-features.md` — F7(북마크), F8(푸시 알림)
- 아키텍처: `docs/planning/03-architecture.md` — §3.2 인증, §3.3 DB 스키마
- 로드맵: `docs/planning/06-roadmap.md` — Phase 4
- 법적 고려: `docs/planning/07-legal-compliance.md` — 개인정보 처리방침 필요

---

## 2. Scope

### 2.1 In Scope

- [ ] **인증 (Phase 4.0)**: Supabase Auth 프론트 연동 (이메일 + Google OAuth), Spring Boot JWT 검증, `/api/me` 엔드포인트
- [ ] **북마크 (Phase 4.1)**: 종목 북마크 CRUD API, 종목 페이지 북마크 토글 UI, 마이페이지 (북마크 목록 + 간단 스냅샷)
- [ ] **알림 (Phase 4.2)**: Service Worker + Web Push 구독/해지, 알림 조건 설정 (가격 변동, 새 뉴스, 시그널 변화), Spring Boot 스케줄러 15분 주기 체크 + VAPID 기반 전송

### 2.2 Out of Scope

- 소셜 로그인 (Apple, GitHub 등) — Google OAuth 외 추가 provider는 Phase 5+
- Firebase Cloud Messaging (FCM) — 모바일 앱 대응 시점에 도입
- 포트폴리오 관리 / 가상 매매 — Phase 5+
- 유료 구독 / 결제 — Phase 5+

---

## 3. Requirements

### 3.1 Functional Requirements

| ID | Requirement | Priority | Status |
|----|-------------|----------|--------|
| FR-01 | Supabase Auth를 통한 이메일 회원가입/로그인 | High | Pending |
| FR-02 | Google OAuth 소셜 로그인 | High | Pending |
| FR-03 | Spring Boot에서 Supabase JWT 검증 (Resource Server) | High | Pending |
| FR-04 | `/api/me` — 현재 사용자 정보 반환 | High | Pending |
| FR-05 | 인증 필요 API에 대한 401/403 처리 | High | Pending |
| FR-06 | 종목 북마크 추가/삭제 토글 (`POST /api/bookmarks`, `DELETE /api/bookmarks/{ticker}`) | High | Pending |
| FR-07 | 북마크 목록 조회 (`GET /api/bookmarks`) | High | Pending |
| FR-08 | 마이페이지 — 북마크 목록 + 현재가/변동률 스냅샷 | Medium | Pending |
| FR-09 | Web Push 구독 등록/해지 (`POST /api/push/subscribe`, `DELETE /api/push/unsubscribe`) | Medium | Pending |
| FR-10 | 종목별 알림 조건 설정 (가격 변동 ±%, 새 뉴스, 시그널 변화) | Medium | Pending |
| FR-11 | Spring Boot 스케줄러: 15분마다 북마크 종목 체크 → 조건 충족 시 Web Push 전송 | Medium | Pending |
| FR-12 | 비로그인 사용자는 기존 기능(시세, 차트, AI 분석 등) 그대로 사용 가능 | High | Pending |

### 3.2 Non-Functional Requirements

| Category | Criteria | Measurement Method |
|----------|----------|-------------------|
| Performance | 인증 검증 (JWT decode) < 5ms | Spring Security filter 소요시간 |
| Performance | 북마크 CRUD < 100ms | API 응답시간 |
| Security | Supabase JWT 서명 검증 (RS256/HS256) | 유효하지 않은 토큰으로 401 확인 |
| Security | CORS 설정에 허용 origin만 포함 | 수동 테스트 |
| Security | 개인정보(이메일) 최소 수집, 서버에 비밀번호 미저장 | Supabase Auth 위임 구조 |
| UX | 비로그인 → 북마크 클릭 시 로그인 안내 (blocking 없이) | 수동 테스트 |

---

## 4. Success Criteria

### 4.1 Definition of Done

- [ ] 이메일 회원가입 + 로그인 → JWT 발급 → Spring Boot API 인증 통과
- [ ] Google OAuth 로그인 동작
- [ ] 종목 상세 페이지에서 북마크 토글 동작
- [ ] 마이페이지에서 북마크 목록 + 현재가 표시
- [ ] Web Push 구독 → 알림 조건 설정 → 조건 충족 시 브라우저 알림 수신
- [ ] 비로그인 상태에서 기존 기능 100% 정상 동작

### 4.2 Quality Criteria

- [ ] 인증 관련 API에 대한 통합 테스트
- [ ] JWT 만료/변조 시 401 반환 확인
- [ ] Zero lint errors (FE + BE)
- [ ] Build succeeds (FE + BE)

---

## 5. Risks and Mitigation

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| Supabase Auth 무료 티어 제한 (50K MAU) | Low | Low | MVP 규모에서는 충분. 초과 시 self-hosted 전환 검토 |
| JWT 검증 설정 오류로 보안 취약점 | High | Medium | Supabase JWKS endpoint 사용, Spring Security 공식 설정 따름 |
| Web Push 브라우저 호환성 (Safari 제한) | Medium | Medium | Safari 17+에서 지원됨. 미지원 브라우저엔 graceful fallback (알림 설정 숨김) |
| 스케줄러 부하 (북마크 종목 수 증가) | Medium | Low | 배치 처리 + Redis 캐시 활용. MVP 규모에서는 문제 없음 |
| 개인정보 처리방침 미비 | High | Medium | Phase 4.0 시작 전 `/legal/privacy` 페이지 내용 검토/보완 |
| Upstash 10K commands/day 제한과 스케줄러 충돌 | Medium | Medium | 스케줄러는 DB 직접 조회 위주, Redis는 시세 캐시만. 모니터링 추가 |

---

## 6. Architecture Considerations

### 6.1 Project Level Selection

| Level | Characteristics | Recommended For | Selected |
|-------|-----------------|-----------------|:--------:|
| **Starter** | Simple structure | Static sites, portfolios | ☐ |
| **Dynamic** | Feature-based modules, BaaS integration | Web apps with backend, SaaS MVPs | ☑ |
| **Enterprise** | Strict layer separation, DI, microservices | High-traffic systems | ☐ |

### 6.2 Key Architectural Decisions

| Decision | Options | Selected | Rationale |
|----------|---------|----------|-----------|
| Auth Provider | Supabase Auth / NextAuth / Firebase Auth | **Supabase Auth** | 이미 DB가 Supabase — 통합 용이, 50K MAU 무료 |
| JWT 검증 위치 | FE middleware / BE Security | **Spring Security JWT Resource Server** | API 레벨 보호, 표준 OAuth2 Resource Server |
| OAuth Provider | Google / Apple / GitHub | **Google** (MVP) | 가장 보편적, 추가 provider는 후속 |
| 북마크 저장 | Redis / PostgreSQL | **PostgreSQL** | 영속 데이터, 관계형 쿼리 필요 (JOIN with quote) |
| Push 방식 | Web Push (VAPID) / FCM / SSE | **Web Push (VAPID)** | 무료, 서버 → 브라우저 직접 전송, Service Worker 기반 |
| 스케줄러 | Spring @Scheduled / Quartz / 외부 Cron | **@Scheduled** | 이미 사용 중 (SymbolSyncService), 단순 주기 체크에 적합 |

### 6.3 인증 플로우

```
┌─────────┐    ①로그인     ┌──────────────┐
│  Browser │──────────────▶│ Supabase Auth │
│ (Next.js)│◀──────────────│              │
└────┬─────┘   ②JWT 발급  └──────────────┘
     │
     │ ③ Authorization: Bearer <jwt>
     ▼
┌──────────────────┐    ④ JWKS 검증     ┌──────────────┐
│  Spring Boot     │───────────────────▶│ Supabase JWKS│
│  (Resource Server)│◀──────────────────│   Endpoint   │
└──────────────────┘   ⑤ 서명 확인 OK  └──────────────┘
```

1. FE에서 Supabase Auth JS SDK로 로그인 (이메일 or Google OAuth)
2. Supabase가 JWT 발급 → FE 클라이언트에 저장
3. FE → BE API 호출 시 `Authorization: Bearer <jwt>` 헤더 첨부
4. Spring Security가 Supabase JWKS endpoint로 공개키 가져와 서명 검증
5. 검증 성공 → `SecurityContext`에 사용자 정보(sub = user_id UUID) 세팅

### 6.4 DB 스키마 (Phase 4 추가분)

```sql
-- Flyway: V6__auth_bookmarks.sql

CREATE TABLE bookmarks (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    ticker VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (user_id, ticker)
);

CREATE INDEX idx_bookmarks_user_id ON bookmarks(user_id);

-- Flyway: V7__notification.sql

CREATE TABLE push_subscriptions (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    endpoint TEXT NOT NULL,
    p256dh TEXT NOT NULL,
    auth TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (user_id, endpoint)
);

CREATE TABLE notification_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    ticker VARCHAR(16) NOT NULL,
    price_change_threshold NUMERIC,
    on_new_news BOOLEAN DEFAULT false,
    on_signal_change BOOLEAN DEFAULT false,
    enabled BOOLEAN DEFAULT true,
    UNIQUE (user_id, ticker)
);

CREATE INDEX idx_notification_settings_user_ticker
    ON notification_settings(user_id, ticker);
```

### 6.5 API 엔드포인트 설계

| Method | Path | Auth | Description |
|--------|------|:----:|-------------|
| GET | `/api/me` | ✅ | 현재 사용자 정보 (sub, email) |
| POST | `/api/bookmarks` | ✅ | 북마크 추가 `{ ticker }` |
| DELETE | `/api/bookmarks/{ticker}` | ✅ | 북마크 삭제 |
| GET | `/api/bookmarks` | ✅ | 북마크 목록 (+ 현재가 스냅샷) |
| GET | `/api/bookmarks/check/{ticker}` | ✅ | 특정 종목 북마크 여부 |
| POST | `/api/push/subscribe` | ✅ | Web Push 구독 등록 |
| DELETE | `/api/push/unsubscribe` | ✅ | Web Push 구독 해지 |
| GET | `/api/notifications/settings` | ✅ | 알림 설정 목록 |
| PUT | `/api/notifications/settings/{ticker}` | ✅ | 종목별 알림 조건 설정 |

### 6.6 FE 페이지/컴포넌트 구조

```
apps/web/src/
├── app/
│   ├── auth/
│   │   ├── login/page.tsx          # 로그인 페이지
│   │   ├── signup/page.tsx         # 회원가입 페이지
│   │   └── callback/page.tsx       # OAuth 콜백 처리
│   └── my/
│       └── page.tsx                # 마이페이지 (북마크 목록)
├── features/
│   ├── auth/
│   │   ├── hooks/
│   │   │   ├── use-auth.ts         # Supabase Auth 상태 관리
│   │   │   └── use-session.ts      # JWT 세션 관리
│   │   ├── auth-provider.tsx       # Supabase Auth context
│   │   ├── login-form.tsx          # 이메일 로그인 폼
│   │   ├── social-login.tsx        # Google OAuth 버튼
│   │   └── auth-guard.tsx          # 인증 필요 영역 래퍼
│   ├── bookmark/
│   │   ├── hooks/
│   │   │   └── use-bookmarks.ts    # 북마크 CRUD hooks
│   │   ├── bookmark-button.tsx     # 종목 페이지 북마크 토글
│   │   └── bookmark-list.tsx       # 마이페이지 북마크 목록
│   └── notification/
│       ├── hooks/
│       │   └── use-push.ts         # Web Push 구독 관리
│       ├── notification-settings.tsx # 알림 조건 설정 UI
│       └── push-prompt.tsx         # 푸시 권한 요청 프롬프트
```

---

## 7. Convention Prerequisites

### 7.1 Existing Project Conventions

- [x] `CLAUDE.md` has coding conventions section
- [ ] `docs/01-plan/conventions.md` exists
- [x] ESLint configuration (`.eslintrc.*` / `eslint.config.ts`)
- [x] Prettier configuration
- [x] TypeScript configuration (`tsconfig.json`)

### 7.2 Conventions to Define/Verify

| Category | Current State | To Define | Priority |
|----------|---------------|-----------|:--------:|
| **인증 헤더** | Missing | API 클라이언트에 JWT 자동 첨부 패턴 (React Query default headers) | High |
| **보호 라우트** | Missing | `auth-guard` 래퍼 vs middleware 기반 리다이렉트 | High |
| **BE 인증 어노테이션** | Missing | `@PreAuthorize` vs SecurityFilterChain URL 매칭 | High |
| **에러 코드** | Exists (기본) | 401/403 응답 body 형식 통일 | Medium |

### 7.3 Environment Variables Needed

| Variable | Purpose | Scope | To Be Created |
|----------|---------|-------|:-------------:|
| `NEXT_PUBLIC_SUPABASE_URL` | Supabase 프로젝트 URL | Client | ☑ (이미 존재 가능) |
| `NEXT_PUBLIC_SUPABASE_ANON_KEY` | Supabase anon/public key | Client | ☑ |
| `SUPABASE_JWT_SECRET` | JWT 서명 검증 (Spring Boot) | Server | ☐ |
| `VAPID_PUBLIC_KEY` | Web Push VAPID 공개키 | Client + Server | ☐ |
| `VAPID_PRIVATE_KEY` | Web Push VAPID 비밀키 | Server | ☐ |

---

## 8. Implementation Strategy (Sub-phases)

Phase 4는 범위가 넓으므로 3개 서브 페이즈로 나누어 진행합니다.

### Phase 4.0 — 인증 기반 (High Priority)

1. Supabase Auth 프론트 연동 (`@supabase/supabase-js`, `@supabase/ssr`)
2. 로그인/회원가입 페이지 (이메일 + Google OAuth)
3. Spring Security JWT Resource Server 설정 (JWKS 검증)
4. `/api/me` 엔드포인트
5. FE API 클라이언트에 JWT 자동 첨부
6. 인증 필요 API에 대한 401/403 처리

### Phase 4.1 — 북마크 (High Priority)

1. DB 마이그레이션 (`V6__auth_bookmarks.sql`)
2. 북마크 CRUD API (Controller → Service → Repository)
3. 종목 상세 페이지 북마크 토글 버튼
4. 마이페이지 (북마크 목록 + 현재가 스냅샷)

### Phase 4.2 — Web Push 알림 (Medium Priority)

1. DB 마이그레이션 (`V7__notification.sql`)
2. VAPID 키 생성 + 환경변수 설정
3. Service Worker + Web Push 구독/해지 플로우
4. 알림 조건 설정 API + UI
5. Spring Boot 스케줄러: 15분 주기 체크 → 조건 충족 시 Push 전송

---

## 9. Next Steps

1. [ ] Design 문서 작성 (`auth.design.md`) — Phase 4.0 인증 기반부터
2. [ ] Supabase 프로젝트에서 Auth 설정 확인 (Google OAuth provider 등록)
3. [ ] 개인정보 처리방침 (`/legal/privacy`) 내용 보완
4. [ ] 구현 시작

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 0.1 | 2026-04-16 | Initial draft | Claude + wonseok-han |
