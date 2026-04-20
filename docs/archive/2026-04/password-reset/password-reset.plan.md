---
template: plan
version: 1.0
feature: password-reset
date: 2026-04-20
author: wonseok-han
project: AI Stock Advisor
status: Draft
---

# password-reset Plan

## Executive Summary

| 관점 | 요약 |
|---|---|
| **Problem** | 이메일/비밀번호로 가입한 사용자가 비밀번호를 잊으면 계정 복구 경로가 없다. 로그인 화면에 "비밀번호를 잊으셨나요?" 링크도 없고, 관련 페이지도 미구현. OAuth(Google) 사용자는 문제 없지만 순수 이메일 가입자는 사실상 계정 잠김. |
| **Solution** | Supabase Auth 의 `resetPasswordForEmail` + `updateUser({ password })` 2-step 플로우를 FE 에 구현. 기존 `/auth/callback` 라우트가 `code→session` 교환을 이미 처리하므로 BE 변경 없이 FE 페이지 2개(`/auth/forgot-password`, `/auth/reset-password`) + 폼 컴포넌트 2개 + 로그인 페이지 링크 1개 추가만으로 완결. 메일 템플릿은 이미 Supabase Dashboard 에 적용됨. |
| **Function UX Effect** | 로그인 화면에서 "비밀번호를 잊으셨나요?" 클릭 → 이메일 입력 → 메일 수신 → 링크 클릭 → 새 비밀번호 입력 → 로그인 상태로 홈 이동. 전 과정 FE SPA 라우팅 내에서 자연스럽게 연결. 1시간 만료/사용 완료 링크 오류 UX 가이드 포함. |
| **Core Value** | 이메일 가입자의 계정 접근성 보장 — 계정 잠김으로 인한 이탈 방지. MVP 인증 시스템의 누락된 마지막 표준 플로우를 채워 "정상 작동하는 인증 제품" 수준으로 끌어올림. BE 변경 0 · Supabase 표준 플로우 재사용으로 구현 리스크 최소. |

## 1. Goal

- **G1 (요청 페이지)**: `/auth/forgot-password` 에서 이메일 입력 → `resetPasswordForEmail()` 호출 → "메일 확인하세요" 안내 표시.
- **G2 (재설정 페이지)**: 사용자가 메일 링크 클릭 → `/auth/callback` 으로 code 교환 → `next=/auth/reset-password` 리다이렉트 → 활성 세션에서 새 비밀번호 입력 → `updateUser({ password })` → 홈 이동.
- **G3 (진입점)**: 로그인 페이지(`/auth/login`)에 "비밀번호를 잊으셨나요?" 링크 추가.
- **G4 (에러 UX)**: 만료/사용 완료/잘못된 링크 접근 시 명확한 안내 + 재요청 경로 노출.

## 2. Non-Goals

- **비밀번호 강도 정책 변경**: 기존 `minLength={6}` 유지. 복잡도(특수문자·대소문자) 규칙 신설 안 함.
- **2FA / TOTP 도입**: 현재 2FA 구조 없음. 별도 feature.
- **Magic Link 로그인**: 비밀번호 재설정과 플로우는 유사하지만 별개. 현재 UX 요구 없음.
- **이메일 변경(change email)**: `updateUser({ email })` 플로우. 마이페이지 추후.
- **OAuth 계정의 비밀번호 설정**: Google 로만 가입한 사용자가 비밀번호 추가 설정하는 플로우. 복잡도 대비 효용 낮아 별도 feature.
- **BE API 추가**: Supabase Auth 로 완결 가능. Spring Security JWT 검증 로직 불변.
- **메일 템플릿 작성**: 이미 Supabase Dashboard 에 적용 완료 (사전 작업).
- **비밀번호 재설정 이력 기록**: 감사 로그. MVP 범위 아님.

## 3. Requirements

### 3.1 Functional Requirements

| FR | 요구사항 | 수용 기준 |
|----|---------|-----------|
| FR-01 | 로그인 페이지(`/auth/login`)에 "비밀번호를 잊으셨나요?" 링크 노출 | `page.tsx` 에 `<Link href="/auth/forgot-password">` 추가, 비밀번호 입력 영역 아래 배치 |
| FR-02 | `/auth/forgot-password` 페이지 진입 시 이메일 입력 폼 노출 | `ForgotPasswordForm` 컴포넌트 — email input + "재설정 메일 보내기" 버튼 |
| FR-03 | 이메일 제출 시 `resetPasswordForEmail(email, { redirectTo: '${origin}/auth/callback?next=/auth/reset-password' })` 호출 | Supabase SDK 표준 호출, redirectTo 는 `window.location.origin` 기반 |
| FR-04 | 제출 성공 시 "메일을 확인해 주세요" 안내 화면 표시 (signup 성공 UX 와 유사한 톤) | 폼 제출 후 `result === 'sent'` 상태로 전환 — 로그인 페이지 링크 포함 |
| FR-05 | 메일 링크 클릭 → `/auth/callback` → 세션 교환 → `/auth/reset-password` 로 이동 | 기존 callback 라우트 `next` 쿼리 처리 활용, **callback 수정 불필요** |
| FR-06 | `/auth/reset-password` 진입 시 활성 세션 확인 | `useEffect` 로 `getUser()` 호출, 없거나 만료면 "링크 만료/무효" 안내 + `/auth/forgot-password` 재요청 링크 |
| FR-07 | 새 비밀번호 + 확인 입력 (`minLength={6}`, 일치 검증) | signup-form 과 동일 검증 로직 |
| FR-08 | 제출 시 `updateUser({ password })` 호출 → 성공 시 홈(`/`)으로 이동 + `router.refresh()` | login-form 과 동일 post-success 패턴 |
| FR-09 | 에러 발생 시 한국어 안내 (`Invalid login credentials` 같은 영문 메시지는 매핑) | login-form 에러 매핑 방식 재사용 |
| FR-10 | 이미 로그인된 상태에서 `/auth/forgot-password` 접근 시 홈으로 리다이렉트 (선택) | 선택 — UX 개선, 구현 시 `auth-provider` 활용 |

### 3.2 Non-Functional Requirements

| NFR | 요구사항 |
|-----|---------|
| NFR-01 | 파일명 `kebab-case` 준수 (`forgot-password-form.tsx`, `reset-password-form.tsx`) — CLAUDE.md FE 컨벤션 |
| NFR-02 | BE(`apps/api`) 변경 0 파일 — Supabase Auth 로 완결 |
| NFR-03 | Next.js 16 App Router 규격 준수 (`page.tsx`, `metadata` export) — `AGENTS.md` 경고 대응 |
| NFR-04 | 기존 auth 폼(login/signup) 과 스타일 일관성 (Tailwind 클래스 그대로 재사용) |
| NFR-05 | 다크모드 지원 (기존 `dark:` 클래스 패턴 유지) |
| NFR-06 | `make web-check` 통과 (tsc `--noEmit` + eslint) |
| NFR-07 | PR 1건 squash merge (`feat/password-reset` → develop) |
| NFR-08 | CLAUDE.md 의 "투자 자문 아님" 원칙은 auth 범위 밖 — 별도 면책 문구 불필요 |

## 4. Scope & Impact

### 4.1 FE 변경 파일

| 파일 | 변경 | LOC (예상) |
|------|------|:---:|
| `apps/web/src/app/auth/forgot-password/page.tsx` | **신규** — 요청 페이지 shell | ~30 |
| `apps/web/src/features/auth/forgot-password-form.tsx` | **신규** — `resetPasswordForEmail()` 호출 폼 | ~90 |
| `apps/web/src/app/auth/reset-password/page.tsx` | **신규** — 재설정 페이지 shell (세션 체크 포함) | ~40 |
| `apps/web/src/features/auth/reset-password-form.tsx` | **신규** — `updateUser({ password })` 호출 폼 | ~110 |
| `apps/web/src/app/auth/login/page.tsx` | 수정 — "비밀번호를 잊으셨나요?" `<Link>` 추가 | +5 |

**합계**: 5 파일 (4 신규 + 1 수정), 약 275 LOC 순증.

### 4.2 BE 변경 파일

**없음.** Supabase Auth 가 토큰 검증·비밀번호 해싱·이메일 발송 전부 처리. Spring Security Resource Server 는 `updateUser` 후 발행되는 JWT 를 그대로 검증.

### 4.3 DB 변경

**없음.** `auth.users` 테이블(Supabase 관리 영역)의 `encrypted_password` 가 업데이트됨. 프로젝트 도메인 테이블 무관.

### 4.4 영향받지 않는 부분

- `apps/web/src/app/auth/callback/route.ts` — 기존 `next` 쿼리 처리 로직 그대로 재사용
- `apps/web/src/features/auth/login-form.tsx` — 수정 없음 (로그인 페이지의 link 만 page.tsx 수준에서 추가)
- `apps/web/src/features/auth/signup-form.tsx` — 무관
- `apps/web/src/features/auth/auth-provider.tsx` — 무관 (세션 변경 감지는 기존 로직으로 충분)
- `apps/web/src/features/auth/social-login.tsx` — 무관

### 4.5 재사용 대상

- `createClient()` (`@/lib/supabase/client`) — 기존 Supabase browser client
- 기존 Tailwind 폼 스타일 클래스 (signup-form / login-form 과 동일)
- `useRouter`, `useState` 표준 패턴

### 4.6 Supabase 사전 조건 (확인)

- [x] Email Templates → Reset Password 템플릿 적용 (Dashboard 설정 완료)
- [ ] Authentication → URL Configuration → Redirect URLs allowlist 에 다음 추가 필요:
  - `http://localhost:3000/**`
  - `https://*.vercel.app/**` (preview)
  - production 도메인 `/**`
- [ ] Authentication → Providers → Email → "Confirm password change" 기본 정책 확인 (현재 기본값으로 충분 예상)

## 5. Risks

| 리스크 | 영향 | 완화 |
|--------|-----|------|
| `/auth/reset-password` 에 세션 없이 직접 접근 | UX 혼란 (빈 화면 · 제출 시 401) | FR-06 세션 체크 → "링크 만료/무효" 안내 + 재요청 링크 표시 |
| 메일 링크 1시간 만료 후 사용 시도 | `updateUser()` 실패 | Supabase 에러 메시지 매핑 — "링크가 만료되었습니다. 새로 요청해 주세요." |
| 이미 사용된 링크 재접근 (뒤로가기 · 새 탭) | 혼란 | 동일 에러 매핑 — 1회성 링크 안내 |
| Redirect URL allowlist 미설정 | 메일 링크 클릭 → Supabase 에러 화면 | **Dashboard 사전 확인 필수** (Success Criteria 체크리스트) |
| Next.js 16 breaking changes | 라우팅/metadata/Link API 변경 가능성 | `AGENTS.md` 경고 따라 `node_modules/next/dist/docs/` 참조. 기존 `/auth/login/page.tsx` 패턴 그대로 답습 |
| 이미 로그인된 사용자가 실수로 reset-password 접근 | 본인 비밀번호 변경됨 (의도된 동작이긴 함) | FR-10 리다이렉트 (선택 사항) — 구현 생략 시 큰 문제는 아님 |
| Supabase 무료 SMTP rate limit | 대량 재요청 시 메일 미발송 | MVP 1인 트래픽이라 현실적 이슈 아님. 문제 시 Resend SMTP 전환 (별도 feature) |
| FE 에러 메시지 유출 (존재 여부 노출) | 보안 미세 이슈 | Supabase `resetPasswordForEmail` 는 존재 여부 무관 성공 응답 (정책 상 안전). 별도 처리 불필요 |

## 6. Success Criteria

### 6.1 기능 동작

- [ ] 로그인 페이지에 "비밀번호를 잊으셨나요?" 링크 노출 및 클릭 시 `/auth/forgot-password` 이동
- [ ] `/auth/forgot-password` 에서 이메일 입력 → "메일 확인하세요" 안내 표시
- [ ] 등록된 이메일로 재설정 메일 실제 수신 (Dashboard 템플릿 렌더링 포함)
- [ ] 메일 링크 클릭 → `/auth/callback` → `/auth/reset-password` 도달 (활성 세션 보유)
- [ ] 새 비밀번호 입력 → `updateUser()` 성공 → 홈 이동 → 로그인 상태 유지
- [ ] 변경된 비밀번호로 재로그인 성공
- [ ] 만료된 링크 재사용 시 명확한 에러 안내 + 재요청 링크

### 6.2 코드 품질

- [ ] `make web-check` BUILD SUCCESS (tsc + eslint 0 errors)
- [ ] `make web-build` BUILD SUCCESS
- [ ] 신규 파일 모두 kebab-case
- [ ] 다크모드 클래스 적용
- [ ] 기존 auth 폼과 스타일 일관성

### 6.3 프로세스

- [ ] `feat/password-reset` 브랜치 생성
- [ ] `/pdca design password-reset` 으로 Design 문서 작성
- [ ] 구현 완료 후 `/pdca analyze password-reset` Gap 분석 ≥ 90%
- [ ] PR squash merge to `develop`

## 7. Dependencies

### 7.1 Supabase Dashboard 설정 (사용자 수동)

1. Authentication → Email Templates → Reset Password (적용 완료)
2. Authentication → URL Configuration → Redirect URLs 에 `/auth/callback` 포함 URL 등록
3. Authentication → Providers → Email → 기본 설정 유지

### 7.2 기술 의존성

- `@supabase/ssr` (server client) · `@supabase/supabase-js` (browser client) — 이미 설치됨
- Next.js 16 — 이미 설치됨
- 추가 의존성 설치 **없음**

## 8. Next Steps

1. `/pdca design password-reset` — Design 문서 (파일별 구현 상세, 상태 머신, 에러 매핑 표)
2. `feat/password-reset` 브랜치 체크아웃
3. 구현 (Do) — 5 파일 작업, `make web-check` 통과 확인
4. `/pdca analyze password-reset` — Gap 분석
5. 필요 시 `/pdca iterate` → `/pdca report` → `/pdca archive`
