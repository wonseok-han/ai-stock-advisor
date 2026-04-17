# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.1.0] - 2026-04-17 - Phase 4.5 Complete (Data Layer + UX Improvements)

### 📊 Added - Candle Data Layer & Infrastructure

- **Candle Database Layer (on-demand)**
  - Flyway V8: `candles` table (ticker, trade_date, OHLCV, adj_close, volume)
  - CandleEntity + CandleId (composite key) + CandleRepository
  - YahooFinanceClient: Yahoo Finance v8 REST API (free, no API key)
  - On-demand loading: DB-first → Yahoo fallback → async persist
  - Partial data detection: triggers reload if entities < 50% of expected

- **Daily Candle Batch Scheduler**
  - Scheduled job: MON-FRI 22:00 UTC (EST 17:00, after market close)
  - Auto-syncs open tickers with fresh daily candles
  - INSERT ON CONFLICT DO NOTHING (prevents duplicates)

- **Rate Limiter (Token Bucket)**
  - IP-based, custom Token Bucket implementation (no Bucket4j)
  - Default: 60 req/min capacity, 60 tokens/min refill
  - Returns 429 when limit exceeded
  - Public chain filter: protects `/api/v1/**` endpoints

### 🎨 Added - My Page Redesign & Notifications

- **My Page Components**
  - ProfileSection: Avatar (initials) + email + join date
  - BookmarkGrid + BookmarkCard: Card grid layout with price/change%
  - NotificationSection: Global push toggle + per-stock settings
  - AccountSection: Logout + delete account buttons
  - DeleteAccountModal: Reason input + 2-year retention notice

- **Stock Detail Notifications**
  - NotificationButton: Bell icon in stock header (beside bookmark)
  - NotificationSettingModal: Price threshold ± %, news alerts, signal changes
  - Auto-bookmark: Non-bookmarked stocks auto-added when setting notifications
  - Notification delete: Available from both My page and stock detail

- **Chart Improvements**
  - Volume histogram subchart (colored: green up, red down)
  - `timeVisible` conditional: Only for 1D (intraday) timeframe
  - 1W timeframe: Changed from 30-min × 70 to 1-day × 5 (last 5 trading days)
  - 1M/3M/1Y: DB-backed daily candles (abundant data)
  - 5Y: Weekly aggregation from daily DB candles

### 🔐 Added - Account Management & Privacy

- **Account Deletion (Soft Delete)**
  - Flyway V9: `deleted_accounts` table
  - AccountService: `deleteAccount(userId)` with 2-year retention
  - AuthController: `DELETE /api/v1/me`
  - Local data cleanup: Bookmarks, notifications, push subscriptions
  - Supabase Auth ban: Prevents re-signup attempt
  - Reactivation: `POST /api/v1/auth/reactivate` on re-signup (unbans + deletes record)

- **Privacy Policy Updates**
  - Data collection items: email, bookmarks, notifications, push subscription
  - Retention period: 2 years after account deletion
  - Reactivation procedure: Re-signup allowed after 2-year cooling-off
  - AI disclaimer: Service is for reference only, not investment advice

### 🐛 Fixed - Residual Gaps from Phase 1~4

- **SearchHit.exchange**: FE type now nullable (matches BE Record)
- **Quote.volume**: TwelveData volume field added to response
- **MarketMover.volume**: FE hides when 0 (FMP API limitation)
- **usdKrwChange**: `previousClose` fallback when `change` is null/zero
- **204 No Content**: apiFetch now handles empty responses
- **ESLint set-state-in-effect**: Fixed in NotificationModal + AuthProvider
- **Cursor pointer**: Applied to all buttons (17 FE files)
- **kebab-case files**: Normalized all FE file names (router.replace → useEffect fix)

### 🔧 Changed

- **TimeFrame enum**: `Duration` → `long lookbackDays` (LocalDate compatibility fix)
- **CandleService**: DB-first logic replaces TwelveData-first for daily+ timeframes
- **1Y/5Y data**: TwelveData weekly API replaced with DB daily + aggregation
- **FE components**: File naming standardized to kebab-case (except Next.js reserved files)
- **Auth design doc**: Documented two-chain + ES256+RS256 implementation differences

### 📝 Documentation

- **Plan**: `docs/01-plan/features/phase4.5-improvements.plan.md` (10 FR + 6 NFR)
- **Design**: `docs/02-design/features/phase4.5-improvements.design.md` (14 steps)
- **Analysis**: `docs/03-analysis/phase4.5-improvements.analysis.md` (96.4% match rate)
- **Report**: `docs/04-report/features/phase4.5-improvements.report.md` (completion)

---

## [1.0.0] - 2026-04-17 - Phase 4 Complete

### 🔐 Added - Authentication & Personalization

- **Supabase Auth Integration**
  - Email/password signup and login
  - Google OAuth authentication (via Supabase)
  - JWT token management (access + refresh tokens)
  - OAuth callback with server-side code exchange
  
- **Spring Security Configuration**
  - JWT Resource Server with Supabase JWKS validation
  - Two-chain SecurityFilterChain for public/protected APIs
  - Support for RS256 and ES256 JWT algorithms
  - Automatic JWT extraction from Authorization header

- **User Authentication API**
  - `GET /api/v1/me` — Current user info (id, email)
  - JWT validation and refresh token flow
  - 401/403 error handling

- **Bookmark Feature (Phase 4.1)**
  - `POST /api/v1/bookmarks` — Add bookmark
  - `DELETE /api/v1/bookmarks/{ticker}` — Remove bookmark
  - `GET /api/v1/bookmarks` — List bookmarks with price snapshot
  - `GET /api/v1/bookmarks/check/{ticker}` — Check if bookmarked
  - Optimistic UI updates in FE
  - BookmarkButton component (toggle UI)
  - BookmarkList component (my page display)
  - `use-bookmarks` React Query hook

- **Web Push Notifications (Phase 4.2)**
  - VAPID-based Web Push subscription
  - `POST /api/v1/push/subscribe` — Subscribe to push
  - `DELETE /api/v1/push/unsubscribe` — Unsubscribe from push
  - `GET /api/v1/push/vapid-key` — Get VAPID public key (secure endpoint)
  - Service Worker for push event handling
  - NotificationSettings UI for per-stock conditions
  - Condition types: price change ±%, new news, signal change
  - `@Scheduled` background job: 15-min interval checker

- **Notification Management API**
  - `GET /api/v1/notifications/settings` — List notification rules
  - `PUT /api/v1/notifications/settings/{ticker}` — Update rule
  - Per-stock notification conditions (price, news, signal)

- **FE Authentication Components**
  - AuthProvider (Supabase context)
  - LoginForm, SignupForm, SocialLogin
  - UserMenu (header dropdown)
  - AuthGuardModal (non-blocking auth prompt)
  - `use-auth` hook
  - JWT auto-attach to API requests
  - 401 interceptor with refresh token retry

- **Pages**
  - `/auth/login` — Email/password login
  - `/auth/signup` — Email/password registration
  - `/auth/callback` — OAuth redirect handler
  - `/my` — My page (bookmarks + notification settings)

- **Database Migrations**
  - `V6__auth_bookmarks.sql` — bookmarks table (user_id, ticker)
  - `V7__notification.sql` — push_subscriptions, notification_settings tables
  - Indexes for user_id and (user_id, ticker) queries

### 🔄 Changed

- **SecurityFilterChain Architecture**
  - Migrated from single-chain to two-chain (@Order 1, 2)
  - Chain 1 (public): `/api/v1/stocks/**`, `/api/v1/market/**` → permitAll()
  - Chain 2 (protected): `/api/v1/me`, `/api/v1/bookmarks/**`, `/api/v1/push/**`, `/api/v1/notifications/**` → authenticated()
  - **Benefit**: No JWT JWKS validation overhead on public APIs

- **JWT Algorithm Support**
  - Added ES256 support (Supabase Auth v2 uses this by default)
  - Kept RS256 fallback for compatibility

- **VAPID Key Management**
  - Moved from FE environment variable to BE-only storage
  - Created `GET /api/v1/push/vapid-key` API endpoint
  - **Benefit**: Public key not exposed in build artifacts

- **API Response Types**
  - BookmarkResponse: `price` as BigDecimal (financial precision)
  - NotificationSettingResponse: Enhanced with full settings

### 🐛 Fixed

- **Public API 401 Errors**
  - Root cause: Single SecurityFilterChain tried to validate JWT on all APIs
  - Fix: Two-chain separation → public APIs skip JWT validation
  - Commit: 8417156

- **Hibernate Data Loss on Restart**
  - Root cause: ddl-auto=create-drop losing data on DB restart
  - Fix: Changed to ddl-auto=validate mode
  - Commit: 8710603

- **Supabase Bulk Insert Timeout**
  - Root cause: Batch size too large during symbol sync
  - Fix: Optimized batch insert size (1000 records per batch)
  - Commit: 8710603

- **CORS on Protected Endpoints**
  - Root cause: CORS config didn't apply to protected chain
  - Fix: WebCorsConfig bean added with allowCredentials(true)
  - Commit: 7532540

- **API Path Prefix Duplication**
  - Root cause: `/api/v1` prefix in both base path and endpoint
  - Fix: Removed duplication, standardized to `/api/v1/**`
  - Commit: 7532540

- **OAuth Callback Security**
  - Root cause: Using page.tsx with client-side code exchange
  - Fix: Migrated to route.ts (API route) with server-side code exchange
  - Benefit: Prevents token exposure on client

- **Bookmark List Path**
  - Root cause: Link pointed to `/stocks` instead of `/stock`
  - Fix: Corrected path to `/stock`
  - Commit: 3373c79

### 🔒 Security

- Implemented two-chain SecurityFilterChain for defense-in-depth
- VAPID public key served via secure API endpoint (not exposed in env)
- Server-side OAuth code exchange (XSS prevention)
- CORS origin whitelist configured
- JWT signature validation with Supabase JWKS endpoint
- No password storage (delegated to Supabase)

### 📊 Test Coverage

- Integration tests for JWT validation and 401/403 responses
- Bookmark CRUD API tests (with PostgreSQL Testcontainers)
- NotificationCheckService @Scheduled task tests
- Manual E2E testing: login → bookmark → push notification

### 📝 Documentation

- **Plan**: `docs/01-plan/features/auth.plan.md` (12 FR + 6 NFR)
- **Design**: `docs/02-design/features/auth.design.md` (comprehensive architecture)
- **Analysis**: `docs/03-analysis/auth.analysis.md` (95% match rate)
- **Report**: `docs/04-report/auth.report.md` (completion report)

---

## Metrics Summary

### Implementation Statistics
- **Total Commits**: 8
- **Files Changed**: 75
- **Lines Added**: +3,424
- **Lines Removed**: -50
- **Net Change**: +3,374

### Code Quality
- **Design Match Rate**: 95% ✅
- **Convention Compliance**: 97% ✅
- **Lint Errors**: 0 ✅
- **Security Issues**: 0 Critical ✅

### Performance
- JWT decode: ~2ms (via cached JWKS)
- Bookmark CRUD: ~50ms avg
- API response: <100ms (design target met)

### Coverage
- BE Endpoints: 9/9 implemented
- FE Components: 7/7 implemented
- FE Hooks: 4/4 implemented
- FE Pages: 4/4 implemented
- DB Migrations: 2/2 implemented

---

## Architecture Improvements

### Two-Chain SecurityFilterChain
```java
// Before: Single chain with requestMatchers
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/v1/stocks/**").permitAll()
    .requestMatchers("/api/v1/bookmarks/**").authenticated()
    // Problem: JWT validation attempted on all endpoints
)

// After: Two separate chains
@Bean @Order(1)
SecurityFilterChain publicFilterChain(...) { 
    // /api/v1/stocks/**, /api/v1/market/** → permitAll()
    // No JWT validation
}

@Bean @Order(2)
SecurityFilterChain protectedFilterChain(...) {
    // /api/v1/bookmarks/**, /api/v1/push/**, etc. → authenticated()
    // JWT validation via JWKS
}
```

**Benefit**: Public API requests skip expensive JWT JWKS validation

### Intentional Design Improvements

| Item | Design | Implementation | Reason |
|------|--------|----------------|--------|
| Chain Strategy | Single | Two-chain | Performance |
| JWT Algorithms | RS256 only | ES256 + RS256 | Compatibility |
| OAuth Callback | page.tsx | route.ts | Security |
| VAPID Scope | Env vars | API endpoint | FE Security |
| AuthGuard | Redirect | Modal | UX |
| Price Type | double | BigDecimal | Precision |

---

## Breaking Changes

None. Phase 4 is additive:
- All existing APIs (`/api/v1/stocks/**`, `/api/v1/market/**`) remain unchanged
- Non-authenticated users can still use public endpoints
- Protected endpoints are new, don't affect existing code

---

## Migration Guide

### For Users (Upgrade from Phase 3)

1. **No breaking changes** — existing features work as before
2. **New login option**: Visit `/auth/login` or `/auth/signup`
3. **New features**:
   - Bookmark stocks: Click ★ on stock detail page
   - My page: View your bookmarks at `/my`
   - Notifications: Set conditions in My page → Notifications

### For Developers

1. **Environment Variables**: Add `NEXT_PUBLIC_SUPABASE_*` and `SUPABASE_JWT_SECRET`
2. **Protected Endpoints**: Use `@Authenticated` principal parameter
3. **FE Auth**: Use `useAuth()` hook to check login status
4. **API Calls**: JWT auto-attached by fetch interceptor

---

## Upcoming in Phase 5

- [ ] Portfolio simulation (virtual trading)
- [ ] AI-powered personalized recommendations
- [ ] Portfolio performance tracking
- [ ] Manual trade logging
- [ ] Additional OAuth providers (Apple, GitHub)
- [ ] FCM support for mobile
- [ ] Advanced notification filters

---

## Known Issues / Limitations

- Web Push in Safari requires 17.0+ (earlier versions not supported)
- VAPID key rotation requires BE deployment
- Notification checker runs every 15 min (not real-time)
- 50K MAU free tier on Supabase Auth (escalate plan if exceeded)

---

## Contributors

- **wonseok-han** — Implementation, testing, deployment
- **Claude (AI)** — Design, architecture, code review

---

## License

Proprietary - AI Stock Advisor
