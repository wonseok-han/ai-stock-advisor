# Auth Design Document

> **Summary**: Supabase Auth + Spring Security JWT 기반 인증, 북마크 CRUD, Web Push 알림 상세 설계
>
> **Project**: AI Stock Advisor
> **Author**: Claude (AI) + wonseok-han
> **Date**: 2026-04-16
> **Status**: Draft
> **Planning Doc**: [auth.plan.md](../../01-plan/features/auth.plan.md)

---

## 1. Overview

### 1.1 Design Goals

- **인증 위임**: 비밀번호 저장·관리를 Supabase Auth에 위임하여 보안 부담 최소화
- **Stateless JWT**: Spring Boot는 JWT 서명 검증만 수행, 세션 미저장
- **비로그인 호환**: 기존 API (시세, 차트, AI 분석 등) 는 인증 없이 그대로 동작
- **점진적 도입**: Phase 4.0(인증) → 4.1(북마크) → 4.2(알림) 순서로 서브 페이즈 분리

### 1.2 Design Principles

- **FE가 인증 주체**: Supabase Auth JS SDK가 로그인/JWT 발급을 처리, BE는 검증만
- **최소 개인정보**: 서버는 `user_id (UUID)` 만 보관, 이메일/이름은 Supabase에만 저장
- **기존 코드 최소 변경**: 비인증 API는 SecurityFilterChain에서 `permitAll()`, 인증 API만 보호

---

## 2. Architecture

### 2.1 Component Diagram

```
┌──────────────────┐       ┌──────────────────┐
│   Browser        │       │  Supabase Auth   │
│   (Next.js)      │──①──▶│  (JWT 발급)       │
│                  │◀──②──│                  │
└────────┬─────────┘       └──────────────────┘
         │
         │ ③ Authorization: Bearer <jwt>
         ▼
┌──────────────────┐       ┌──────────────────┐
│  Spring Boot     │──④──▶│  Supabase JWKS   │
│  (Resource Server)│◀──⑤──│  Endpoint        │
└────────┬─────────┘       └──────────────────┘
         │
         ├──▶ PostgreSQL (bookmarks, notification_settings, push_subscriptions)
         └──▶ Redis (시세 캐시 — 기존)
```

### 2.2 Data Flow

#### 인증 플로우

```
1. 사용자 → 로그인 폼 (이메일/비밀번호 or Google OAuth)
2. FE → Supabase Auth SDK → supabase.auth.signInWithPassword() / signInWithOAuth()
3. Supabase → JWT(access_token) + refresh_token 발급
4. FE → 쿠키/메모리에 토큰 저장 (Supabase SSR 패키지가 쿠키 자동 관리)
5. FE → BE API 호출 시 Authorization: Bearer <access_token>
6. BE → Spring Security OAuth2 Resource Server → JWKS 공개키로 서명 검증
7. 검증 성공 → SecurityContext에 user_id(sub claim) 세팅
8. 검증 실패 → 401 Unauthorized 반환
```

#### 북마크 플로우

```
종목 상세 → 북마크 버튼 클릭
  → (비로그인) 로그인 유도 모달
  → (로그인) POST /api/v1/bookmarks { ticker: "AAPL" }
  → BE: user_id + ticker → bookmarks 테이블 INSERT (ON CONFLICT IGNORE)
  → 200 OK
```

#### 알림 플로우

```
마이페이지 → 알림 설정
  → FE: Service Worker 등록 + PushManager.subscribe()
  → POST /api/v1/push/subscribe { endpoint, p256dh, auth }
  → BE: push_subscriptions 저장

Spring @Scheduled (15분 주기)
  → 북마크 종목 조회 → 시세/뉴스/시그널 체크
  → 조건 충족 시 Web Push API로 전송
```

### 2.3 Dependencies

| Component | Depends On | Purpose |
|-----------|-----------|---------|
| `@supabase/supabase-js` | FE | Supabase Auth SDK (로그인/JWT 관리) |
| `@supabase/ssr` | FE | SSR 환경 쿠키 기반 세션 관리 |
| `spring-boot-starter-oauth2-resource-server` | BE | JWT 검증 (JWKS) |
| `web-push` (Java library) | BE | VAPID 기반 Web Push 전송 |

---

## 3. Data Model

### 3.1 Entity Definition

#### BE (Java Records / JPA Entities)

```java
// BookmarkEntity.java
@Entity @Table(name = "bookmarks")
public class BookmarkEntity {
    @Id @GeneratedValue(strategy = IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private UUID userId;
    
    @Column(nullable = false, length = 16)
    private String ticker;
    
    @Column(nullable = false)
    private OffsetDateTime createdAt;
}

// PushSubscriptionEntity.java
@Entity @Table(name = "push_subscriptions")
public class PushSubscriptionEntity {
    @Id @GeneratedValue(strategy = IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private UUID userId;
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String endpoint;
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String p256dh;
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String auth;
    
    @Column(nullable = false)
    private OffsetDateTime createdAt;
}

// NotificationSettingEntity.java
@Entity @Table(name = "notification_settings")
public class NotificationSettingEntity {
    @Id @GeneratedValue(strategy = IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private UUID userId;
    
    @Column(nullable = false, length = 16)
    private String ticker;
    
    private BigDecimal priceChangeThreshold;
    private boolean onNewNews;
    private boolean onSignalChange;
    private boolean enabled;
}
```

#### FE (TypeScript Types)

```typescript
// types/auth.ts
interface AuthUser {
  id: string;          // UUID
  email: string;
  displayName?: string;
}

interface AuthSession {
  accessToken: string;
  refreshToken: string;
  user: AuthUser;
  expiresAt: number;
}

// types/bookmark.ts
interface Bookmark {
  ticker: string;
  name: string;       // 종목명 (JOIN or FE resolve)
  price: number;      // 현재가 스냅샷
  changePercent: number;
  createdAt: string;
}

// types/notification.ts
interface NotificationSetting {
  ticker: string;
  priceChangeThreshold: number | null;
  onNewNews: boolean;
  onSignalChange: boolean;
  enabled: boolean;
}
```

### 3.2 Entity Relationships

```
[Supabase Auth User (UUID)]
    │
    ├── 1 ──── N [Bookmark] (user_id, ticker UNIQUE)
    ├── 1 ──── N [PushSubscription] (user_id, endpoint UNIQUE)
    └── 1 ──── N [NotificationSetting] (user_id, ticker UNIQUE)
```

### 3.3 Database Schema

#### V6__auth_bookmarks.sql

```sql
CREATE TABLE bookmarks (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    ticker VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, ticker)
);

CREATE INDEX idx_bookmarks_user_id ON bookmarks(user_id);
```

#### V7__notification.sql

```sql
CREATE TABLE push_subscriptions (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    endpoint TEXT NOT NULL,
    p256dh TEXT NOT NULL,
    auth TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, endpoint)
);

CREATE TABLE notification_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    ticker VARCHAR(16) NOT NULL,
    price_change_threshold NUMERIC,
    on_new_news BOOLEAN NOT NULL DEFAULT false,
    on_signal_change BOOLEAN NOT NULL DEFAULT false,
    enabled BOOLEAN NOT NULL DEFAULT true,
    UNIQUE (user_id, ticker)
);

CREATE INDEX idx_notification_settings_user_ticker
    ON notification_settings(user_id, ticker);
```

---

## 4. API Specification

### 4.1 Endpoint List

| Method | Path | Auth | Description |
|--------|------|:----:|-------------|
| GET | `/api/v1/me` | ✅ | 현재 사용자 정보 |
| POST | `/api/v1/bookmarks` | ✅ | 북마크 추가 |
| DELETE | `/api/v1/bookmarks/{ticker}` | ✅ | 북마크 삭제 |
| GET | `/api/v1/bookmarks` | ✅ | 북마크 목록 (+현재가) |
| GET | `/api/v1/bookmarks/check/{ticker}` | ✅ | 북마크 여부 확인 |
| POST | `/api/v1/push/subscribe` | ✅ | Push 구독 등록 |
| DELETE | `/api/v1/push/unsubscribe` | ✅ | Push 구독 해지 |
| GET | `/api/v1/notifications/settings` | ✅ | 알림 설정 목록 |
| PUT | `/api/v1/notifications/settings/{ticker}` | ✅ | 알림 조건 수정 |

### 4.2 Detailed Specification

#### `GET /api/v1/me`

JWT에서 추출한 사용자 정보 반환.

**Response (200):**
```json
{
  "id": "a1b2c3d4-...",
  "email": "user@example.com"
}
```

#### `POST /api/v1/bookmarks`

**Request:**
```json
{
  "ticker": "AAPL"
}
```

**Response (201 Created):**
```json
{
  "ticker": "AAPL",
  "createdAt": "2026-04-16T12:00:00Z"
}
```

**Error:**
- `409 Conflict` — 이미 북마크된 종목 (무시하고 200 반환도 고려)

#### `DELETE /api/v1/bookmarks/{ticker}`

**Response (204 No Content)**

#### `GET /api/v1/bookmarks`

북마크 목록 + 각 종목 현재가 스냅샷.

**Response (200):**
```json
{
  "bookmarks": [
    {
      "ticker": "AAPL",
      "name": "Apple Inc.",
      "price": 198.50,
      "changePercent": 1.23,
      "createdAt": "2026-04-15T10:00:00Z"
    }
  ],
  "total": 1
}
```

> 현재가는 기존 `FinnhubClient.quote()` + Redis 캐시 활용. 북마크 수가 많으면 배치 조회.

#### `GET /api/v1/bookmarks/check/{ticker}`

**Response (200):**
```json
{
  "bookmarked": true
}
```

#### `POST /api/v1/push/subscribe`

**Request:**
```json
{
  "endpoint": "https://fcm.googleapis.com/fcm/send/...",
  "keys": {
    "p256dh": "BNcRdreA...",
    "auth": "tBHItJ..."
  }
}
```

**Response (201 Created):**
```json
{
  "subscribed": true
}
```

#### `PUT /api/v1/notifications/settings/{ticker}`

**Request:**
```json
{
  "priceChangeThreshold": 5.0,
  "onNewNews": true,
  "onSignalChange": true,
  "enabled": true
}
```

**Response (200):**
```json
{
  "ticker": "AAPL",
  "priceChangeThreshold": 5.0,
  "onNewNews": true,
  "onSignalChange": true,
  "enabled": true
}
```

---

## 5. UI/UX Design

### 5.1 Screen Layout

#### 로그인 페이지 (`/auth/login`)

```
┌────────────────────────────────────┐
│  SiteHeader (Beta badge)           │
├────────────────────────────────────┤
│                                    │
│  ┌──────────────────────────┐      │
│  │     AI Stock Advisor     │      │
│  │                          │      │
│  │  [이메일 입력]           │      │
│  │  [비밀번호 입력]         │      │
│  │                          │      │
│  │  [로그인] 버튼           │      │
│  │                          │      │
│  │  ─── 또는 ───            │      │
│  │                          │      │
│  │  [G] Google로 계속       │      │
│  │                          │      │
│  │  계정이 없으신가요?      │      │
│  │  회원가입 링크           │      │
│  └──────────────────────────┘      │
│                                    │
├────────────────────────────────────┤
│  DisclaimerFooter                  │
└────────────────────────────────────┘
```

#### 회원가입 페이지 (`/auth/signup`)

```
┌──────────────────────────┐
│  [이메일 입력]           │
│  [비밀번호 입력]         │
│  [비밀번호 확인]         │
│                          │
│  [회원가입] 버튼         │
│                          │
│  이미 계정이 있으신가요?  │
│  로그인 링크             │
└──────────────────────────┘
```

#### 종목 상세 — 북마크 버튼 (기존 페이지에 추가)

```
┌─ StockDetail Header ───────────────┐
│  AAPL  Apple Inc.       [★ 북마크] │  ← 토글 버튼 추가
│  $198.50  +1.23%                   │
└────────────────────────────────────┘
```

#### 마이페이지 (`/my`)

```
┌────────────────────────────────────┐
│  SiteHeader  [user@email ▾ 로그아웃]│
├────────────────────────────────────┤
│                                    │
│  📌 내 북마크 (3)                  │
│  ┌────────────────────────────┐    │
│  │ AAPL  Apple      $198 +1% │    │
│  │ TSLA  Tesla      $250 -2% │    │
│  │ NVDA  NVIDIA     $850 +3% │    │
│  └────────────────────────────┘    │
│                                    │
│  🔔 알림 설정                      │
│  ┌────────────────────────────┐    │
│  │ AAPL  가격±5% ✅ 뉴스 ✅   │    │
│  │ TSLA  가격±3% ✅ 시그널 ✅  │    │
│  └────────────────────────────┘    │
│                                    │
├────────────────────────────────────┤
│  DisclaimerFooter                  │
└────────────────────────────────────┘
```

### 5.2 User Flow

```
비로그인 사용자:
  메인 → 종목 검색 → 종목 상세 → [북마크 클릭] → 로그인 안내 모달 → 로그인 페이지

로그인 사용자:
  메인 → 종목 상세 → [★ 북마크] 토글 → 즉시 반영 (optimistic update)
  메인 → 헤더 [user ▾] → 마이페이지 → 북마크 목록 → 종목 클릭 → 종목 상세
```

### 5.3 Component List

| Component | Location | Responsibility |
|-----------|----------|----------------|
| `AuthProvider` | `src/features/auth/auth-provider.tsx` | Supabase Auth 상태 관리, context 제공 |
| `LoginForm` | `src/features/auth/login-form.tsx` | 이메일/비밀번호 로그인 폼 |
| `SocialLogin` | `src/features/auth/social-login.tsx` | Google OAuth 버튼 |
| `AuthGuard` | `src/features/auth/auth-guard.tsx` | 인증 필요 영역 래퍼 (미인증 시 리다이렉트) |
| `UserMenu` | `src/features/auth/user-menu.tsx` | 헤더 우측 사용자 메뉴 (로그인/로그아웃) |
| `BookmarkButton` | `src/features/bookmark/bookmark-button.tsx` | 종목 상세 북마크 토글 |
| `BookmarkList` | `src/features/bookmark/bookmark-list.tsx` | 마이페이지 북마크 목록 |
| `NotificationSettings` | `src/features/notification/notification-settings.tsx` | 알림 조건 설정 UI |
| `PushPrompt` | `src/features/notification/push-prompt.tsx` | 브라우저 푸시 권한 요청 |

---

## 6. Error Handling

### 6.1 Error Code Definition

| Code | Message | Cause | Handling |
|------|---------|-------|----------|
| 401 | `Unauthorized` | JWT 없음/만료/변조 | FE: refresh token 시도 → 실패 시 로그인 리다이렉트 |
| 403 | `Forbidden` | 권한 없음 (타인 리소스) | FE: 에러 표시 |
| 404 | `Not Found` | 존재하지 않는 북마크 등 | FE: 무시 (삭제 시) |
| 409 | `Conflict` | 이미 존재하는 북마크 | FE: 무시 (이미 북마크됨 상태로 표시) |
| 422 | `Validation Error` | 잘못된 입력 (이메일 형식 등) | FE: 필드별 에러 표시 |

### 6.2 Error Response Format

기존 프로젝트 에러 형식 유지:

```json
{
  "error": {
    "code": "UNAUTHORIZED",
    "message": "인증이 필요합니다.",
    "requestId": "req_abc123",
    "timestamp": "2026-04-16T12:00:00Z"
  }
}
```

### 6.3 JWT 갱신 전략

```
API 호출 → 401 응답
  → FE: supabase.auth.refreshSession() 호출
  → 성공: 새 access_token 으로 원래 요청 재시도
  → 실패: 로그인 페이지로 리다이렉트
```

`apiFetch()` (기존 `client.ts`) 에 401 인터셉터 추가.

---

## 7. Security Considerations

### 7.1 Checklist

- [x] HTTPS 강제 (Vercel + Render 기본 적용)
- [ ] JWT 서명 검증 (Supabase JWKS endpoint, RS256)
- [ ] CORS `allowCredentials: true` + 허용 origin 제한
- [ ] CSRF: Stateless JWT 사용으로 CSRF 토큰 불필요 (쿠키에 `SameSite=Lax`)
- [ ] XSS: 이메일/사용자 입력 sanitize (React 기본 escape + 서버 validation)
- [ ] Rate Limiting: 로그인 시도 횟수 제한 (Supabase Auth 내장 보호)
- [ ] 개인정보: 서버는 UUID만 저장, 이메일/비밀번호는 Supabase 관리

### 7.2 Spring Security 설정

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable()) // Stateless JWT — CSRF 불필요
            .sessionManagement(sm -> sm.sessionCreationPolicy(STATELESS))
            .authorizeHttpRequests(auth -> auth
                // 기존 비인증 API — 그대로 유지
                .requestMatchers("/api/v1/stocks/**").permitAll()
                .requestMatchers("/api/v1/market/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                // 인증 필요 API
                .requestMatchers("/api/v1/me").authenticated()
                .requestMatchers("/api/v1/bookmarks/**").authenticated()
                .requestMatchers("/api/v1/push/**").authenticated()
                .requestMatchers("/api/v1/notifications/**").authenticated()
                .anyRequest().permitAll()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .jwkSetUri(supabaseJwksUri)
                    .jwtAuthenticationConverter(supabaseJwtConverter())
                )
            )
            .build();
    }
}
```

### 7.3 Supabase JWT 구조

```json
{
  "sub": "a1b2c3d4-...",     // user_id (UUID) — BE에서 이것만 사용
  "email": "user@example.com",
  "role": "authenticated",
  "iss": "https://{ref}.supabase.co/auth/v1",
  "aud": "authenticated",
  "exp": 1713264000
}
```

BE `JwtAuthenticationConverter` 에서 `sub` claim → `userId` 로 매핑.

---

## 8. Test Plan

### 8.1 Test Scope

| Type | Target | Tool |
|------|--------|------|
| Unit Test | BookmarkService, NotificationService | JUnit 5 + Mockito |
| Integration Test | Security filter + JWT 검증 | Spring Boot Test + MockMvc |
| Integration Test | Bookmark CRUD API | Testcontainers (PostgreSQL) |
| Manual Test | 로그인/OAuth 플로우 | 브라우저 수동 |
| Manual Test | Web Push 수신 | 브라우저 + Service Worker DevTools |

### 8.2 Test Cases (Key)

- [ ] 유효한 JWT로 `/api/v1/me` 호출 → 200 + 사용자 정보
- [ ] 만료된 JWT로 호출 → 401
- [ ] JWT 없이 `/api/v1/bookmarks` 호출 → 401
- [ ] JWT 없이 `/api/v1/stocks/AAPL/quote` 호출 → 200 (기존 API 영향 없음)
- [ ] 북마크 추가 → 목록 조회 → 삭제 → 재조회 (CRUD cycle)
- [ ] 동일 종목 중복 북마크 → 409 또는 200 (멱등)
- [ ] Google OAuth 로그인 → 콜백 → JWT 발급 → API 호출 성공

---

## 9. Clean Architecture

### 9.1 BE Layer Structure

| Layer | Responsibility | Location |
|-------|---------------|----------|
| **Controller** | HTTP 요청/응답, 인증 사용자 추출 | `auth/controller/`, `bookmark/controller/` |
| **Service** | 비즈니스 로직 | `auth/service/`, `bookmark/service/`, `notification/service/` |
| **Repository** | DB 접근 (JPA) | `bookmark/infra/`, `notification/infra/` |
| **Domain** | 엔티티, DTO, 도메인 모델 | `auth/domain/`, `bookmark/domain/`, `notification/domain/` |
| **Config** | Spring Security 설정 | `common/security/` |

### 9.2 FE Layer Structure

| Layer | Responsibility | Location |
|-------|---------------|----------|
| **Pages** | 라우트 페이지 | `app/auth/`, `app/my/` |
| **Features** | 기능별 컴포넌트 + 훅 | `features/auth/`, `features/bookmark/`, `features/notification/` |
| **Lib/API** | API 클라이언트 | `lib/api/auth.ts`, `lib/api/bookmarks.ts` |
| **Types** | 타입 정의 | `types/auth.ts`, `types/bookmark.ts`, `types/notification.ts` |

---

## 10. Coding Convention Reference

### 10.1 This Feature's Conventions

| Item | Convention Applied |
|------|-------------------|
| FE 파일명 | kebab-case (`auth-provider.tsx`, `bookmark-button.tsx`) |
| FE 컴포넌트명 | PascalCase (`AuthProvider`, `BookmarkButton`) |
| BE 패키지 | domain-driven (`auth/`, `bookmark/`, `notification/`) |
| BE DTO | `*Request` / `*Response` (`BookmarkRequest`, `BookmarkResponse`) |
| Import 순서 | 외부 → `@/` 절대경로 → 상대경로 → `import type` |
| 환경변수 | `NEXT_PUBLIC_SUPABASE_*` (FE), `SUPABASE_JWT_SECRET` (BE) |

### 10.2 Environment Variables (Phase 4 추가분)

| Variable | Prefix | Scope | Phase |
|----------|--------|-------|:-----:|
| `NEXT_PUBLIC_SUPABASE_URL` | `NEXT_PUBLIC_` | Client | 4.0 |
| `NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY` | `NEXT_PUBLIC_` | Client | 4.0 |
| `SUPABASE_JWT_SECRET` | - | Server | 4.0 |
| `VAPID_PUBLIC_KEY` | - | Server + Client | 4.2 |
| `VAPID_PRIVATE_KEY` | - | Server | 4.2 |

---

## 11. Implementation Guide

### 11.1 File Structure

#### BE (Spring Boot) — 추가 패키지

```
apps/api/src/main/java/com/aistockadvisor/
├── common/
│   └── security/
│       ├── SecurityConfig.java            # SecurityFilterChain 설정
│       ├── JwtAuthenticationConverter.java # sub → userId 매핑
│       └── AuthenticatedUser.java         # 컨트롤러 파라미터 resolver
├── auth/
│   ├── controller/
│   │   └── AuthController.java            # GET /api/v1/me
│   └── domain/
│       └── MeResponse.java                # { id, email }
├── bookmark/
│   ├── controller/
│   │   └── BookmarkController.java        # CRUD endpoints
│   ├── service/
│   │   └── BookmarkService.java
│   ├── infra/
│   │   ├── BookmarkEntity.java
│   │   └── BookmarkRepository.java
│   └── domain/
│       ├── BookmarkRequest.java
│       ├── BookmarkResponse.java
│       └── BookmarkListResponse.java
├── notification/
│   ├── controller/
│   │   ├── PushController.java            # subscribe/unsubscribe
│   │   └── NotificationController.java    # settings CRUD
│   ├── service/
│   │   ├── PushService.java               # Web Push 전송
│   │   └── NotificationCheckService.java  # 15분 스케줄러
│   ├── infra/
│   │   ├── PushSubscriptionEntity.java
│   │   ├── PushSubscriptionRepository.java
│   │   ├── NotificationSettingEntity.java
│   │   └── NotificationSettingRepository.java
│   └── domain/
│       ├── PushSubscribeRequest.java
│       └── NotificationSettingRequest.java
```

#### FE (Next.js) — 추가 파일

```
apps/web/src/
├── app/
│   ├── auth/
│   │   ├── login/page.tsx
│   │   ├── signup/page.tsx
│   │   └── callback/page.tsx         # OAuth 콜백
│   └── my/
│       └── page.tsx                  # 마이페이지
├── features/
│   ├── auth/
│   │   ├── auth-provider.tsx         # Supabase client + context
│   │   ├── login-form.tsx
│   │   ├── social-login.tsx
│   │   ├── auth-guard.tsx
│   │   ├── user-menu.tsx             # 헤더 우측 로그인/사용자 메뉴
│   │   └── hooks/
│   │       ├── use-auth.ts           # useContext(AuthContext)
│   │       └── use-session.ts        # JWT 토큰 관리
│   ├── bookmark/
│   │   ├── bookmark-button.tsx
│   │   ├── bookmark-list.tsx
│   │   └── hooks/
│   │       └── use-bookmarks.ts      # React Query CRUD hooks
│   └── notification/
│       ├── notification-settings.tsx
│       ├── push-prompt.tsx
│       └── hooks/
│           └── use-push.ts
├── lib/
│   ├── supabase/
│   │   ├── client.ts                 # createBrowserClient
│   │   └── server.ts                 # createServerClient (RSC용)
│   └── api/
│       ├── auth.ts                   # /api/v1/me
│       └── bookmarks.ts             # /api/v1/bookmarks/*
├── types/
│   ├── auth.ts
│   ├── bookmark.ts
│   └── notification.ts
```

### 11.2 Implementation Order

#### Phase 4.0 — 인증 기반

1. [ ] BE: `spring-boot-starter-oauth2-resource-server` 의존성 추가 (`build.gradle.kts`)
2. [ ] BE: `SecurityConfig.java` — JWKS 기반 JWT 검증 + 기존 API `permitAll()`
3. [ ] BE: `JwtAuthenticationConverter.java` — sub → userId 매핑
4. [ ] BE: `AuthController.java` + `MeResponse.java` — `GET /api/v1/me`
5. [ ] BE: `WebCorsConfig` — `allowCredentials(true)` 추가
6. [ ] FE: `@supabase/supabase-js`, `@supabase/ssr` 패키지 설치
7. [ ] FE: `lib/supabase/client.ts`, `lib/supabase/server.ts` 생성
8. [ ] FE: `AuthProvider` — Supabase Auth 상태 context
9. [ ] FE: 로그인 페이지 (`/auth/login`) + `LoginForm` + `SocialLogin`
10. [ ] FE: 회원가입 페이지 (`/auth/signup`)
11. [ ] FE: OAuth 콜백 페이지 (`/auth/callback`)
12. [ ] FE: `UserMenu` — 헤더에 로그인/사용자 메뉴 추가
13. [ ] FE: `apiFetch()` 에 JWT 자동 첨부 + 401 인터셉터
14. [ ] FE: `.env.local` 에 `NEXT_PUBLIC_SUPABASE_URL`, `NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY` 추가
15. [ ] BE: `.env.local` 에 `SUPABASE_JWT_SECRET` 추가
16. [ ] 통합 테스트: 로그인 → JWT 발급 → `/api/v1/me` 호출 성공

#### Phase 4.1 — 북마크

17. [ ] BE: `V6__auth_bookmarks.sql` Flyway 마이그레이션
18. [ ] BE: `BookmarkEntity` + `BookmarkRepository`
19. [ ] BE: `BookmarkService` + `BookmarkController` (CRUD)
20. [ ] FE: `lib/api/bookmarks.ts` — API 클라이언트
21. [ ] FE: `use-bookmarks.ts` — React Query 훅 (add/remove/list/check)
22. [ ] FE: `BookmarkButton` — 종목 상세에 토글 버튼
23. [ ] FE: `AuthGuard` — 비인증 시 로그인 유도 모달
24. [ ] FE: 마이페이지 (`/my`) + `BookmarkList`

#### Phase 4.2 — Web Push 알림

25. [ ] BE: `V7__notification.sql` Flyway 마이그레이션
26. [ ] BE: Web Push 라이브러리 의존성 추가
27. [ ] BE: VAPID 키 생성 + 환경변수 설정
28. [ ] BE: `PushSubscriptionEntity` + `PushSubscriptionRepository`
29. [ ] BE: `PushService` — 구독 관리 + Push 전송
30. [ ] BE: `NotificationSettingEntity` + `NotificationSettingRepository`
31. [ ] BE: `NotificationController` — 알림 조건 설정 CRUD
32. [ ] BE: `NotificationCheckService` — `@Scheduled(cron)` 15분 주기 체크
33. [ ] FE: Service Worker 등록 (`public/sw.js`)
34. [ ] FE: `PushPrompt` — 브라우저 푸시 권한 요청
35. [ ] FE: `use-push.ts` — PushManager 구독 관리
36. [ ] FE: `NotificationSettings` — 마이페이지 알림 조건 UI

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 0.1 | 2026-04-16 | Initial draft | Claude + wonseok-han |
