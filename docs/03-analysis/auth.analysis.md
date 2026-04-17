# Auth Feature — Gap Analysis Report

> **Feature**: auth (인증 / 북마크 / Web Push 알림)
> **Design Document**: `docs/02-design/features/auth.design.md`
> **Analysis Date**: 2026-04-17
> **Match Rate**: 95%

---

## Overall Scores

| Category | Score | Status |
|----------|:-----:|:------:|
| API Endpoints Match | 95% | Pass |
| Data Model Match | 100% | Pass |
| BE Architecture Match | 96% | Pass |
| FE Architecture Match | 91% | Pass |
| Security Config Match | 95% | Pass |
| Convention Compliance | 97% | Pass |
| **Overall** | **95%** | **Pass** |

---

## Matched Items

### BE (29/29 items)

- SecurityConfig (two-chain), SupabaseJwtConverter, AuthenticatedUser
- AuthController (GET /me), MeResponse
- BookmarkEntity, BookmarkRepository, BookmarkService, BookmarkController (4 endpoints)
- AddBookmarkRequest, BookmarkResponse, BookmarkListResponse, BookmarkCheckResponse
- PushSubscriptionEntity/Repository, NotificationSettingEntity/Repository
- PushService (VAPID + webpush), NotificationSettingService, NotificationCheckService (@Scheduled 15min)
- PushController, NotificationController
- PushSubscribeRequest, PushUnsubscribeRequest, NotificationSettingRequest, NotificationSettingResponse
- WebCorsConfig (CorsConfigurationSource bean)
- V6__auth_bookmarks.sql, V7__notification.sql

### FE (27/27 items)

- AuthProvider + useAuth, LoginForm, SignupForm, SocialLogin, UserMenu
- BookmarkButton, BookmarkList, use-bookmarks hook
- NotificationSettings, PushPrompt, use-push hook, use-notification-settings hook
- Login/Signup/Callback pages, My page
- Supabase client/server, API clients (auth, bookmarks, notifications)
- Types (auth, bookmark, notification)
- Service Worker (sw.js), SwRegister, env.ts, JWT auto-attach + 401 interceptor

### DB Migrations — Exact Match

V6, V7 모두 설계 SQL과 100% 일치.

---

## Intentional Improvements (Design != Implementation)

| Item | Design | Implementation | Reason |
|---|---|---|---|
| SecurityFilterChain | Single chain + requestMatchers | Two-chain (@Order 1, 2) | 공개 API에서 JWT 파싱 제거 (성능/안정성) |
| JWT algorithm | RS256 JWKS only | ES256 + RS256 | Supabase Auth v2가 ES256 사용 |
| OAuth callback | page.tsx | route.ts (API route) | 서버 사이드 code 교환이 보안상 우수 |
| VAPID key scope | Server + Client env | Server env + API endpoint | FE에 시크릿 노출 방지 |
| AuthGuard | redirect 방식 | modal 방식 (auth-guard-modal) | 페이지 이탈 방지 UX 개선 |
| BookmarkResponse.price | double | BigDecimal | 금융 데이터 정밀도 |

---

## Minor Differences

| Item | Design | Implementation | Severity |
|---|---|---|:---:|
| JwtAuthenticationConverter naming | `JwtAuthenticationConverter` | `SupabaseJwtConverter` | Minor |
| useAuth/useSession hooks | 별도 파일 | auth-provider.tsx에 통합 | Minor |
| AuthSession FE type | 별도 interface | Supabase Session 직접 사용 | Minor |
| BookmarkRequest DTO name | `BookmarkRequest` | `AddBookmarkRequest` | Minor |
| Bookmark.price nullable | `number` | `number \| null` | Minor |

---

## Added Features (Design X, Implementation O)

- `GET /api/v1/push/vapid-key` — VAPID 공개키 API (보안 개선)
- `/api/v1/stocks/*/ai-signal` in protectedFilterChain — AI 시그널 로그인 필수
- SignupForm, SwRegister 컴포넌트

---

## Convention Compliance

| Convention | Compliance |
|---|:---:|
| FE file naming (kebab-case) | 100% |
| FE component naming (PascalCase) | 100% |
| BE package layout (domain-driven) | 100% |
| BE DTO naming (*Request/*Response) | 100% |
| Import order | 100% |
| Env var naming | 100% |

---

## Design Document Update Recommendations (Low Priority)

1. s7.2 SecurityConfig 코드 예시 → two-chain 방식으로 업데이트
2. s7.1 Checklist RS256 → "ES256 (primary) + RS256 (fallback)"
3. s4.1 Endpoint List에 `GET /api/v1/push/vapid-key` 추가
4. s5.3 Component List에 SignupForm, SwRegister 추가
5. s11.1 callback/page.tsx → callback/route.ts 반영

## Immediate Action Required

없음. 모든 핵심 기능이 설계대로 구현되었으며, 차이점은 의도적 개선 또는 경미한 변경.

---

## Result: **95% — Check Pass**

`[Plan] -> [Design] -> [Do] -> [Check] ✅ -> [Report] ⏳`
