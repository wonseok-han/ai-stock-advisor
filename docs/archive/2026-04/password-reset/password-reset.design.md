---
template: design
version: 1.0
feature: password-reset
date: 2026-04-20
author: wonseok-han
project: AI Stock Advisor
status: Draft
---

# password-reset Design

> **Plan**: [password-reset.plan.md](../../01-plan/features/password-reset.plan.md)

---

## 1. Scope & Reference

### 1.1 목표 재확인 (Plan 기준)

- **G1**: `/auth/forgot-password` 에서 이메일 입력 → `resetPasswordForEmail()` 호출 → 안내
- **G2**: 메일 링크 클릭 → `/auth/callback` (기존) → `/auth/reset-password` 로 이동 → `updateUser({ password })`
- **G3**: 로그인 페이지에 "비밀번호를 잊으셨나요?" 링크 추가
- **G4**: 만료/사용 완료 링크 접근 시 명확한 에러 UX

### 1.2 참조 문서

- Phase 4 Auth: `docs/archive/2026-04/auth/` — Supabase Auth 통합, `/auth/callback` 라우트, SSR/CSR 클라이언트 분리
- 본 feature Plan: `docs/01-plan/features/password-reset.plan.md`
- Supabase 공식 문서: Auth Password Reset Flow (PKCE)

### 1.3 기존 인프라 확인 결과 (Pre-design findings)

- **`/auth/callback/route.ts` 는 이미 `next` 쿼리 파라미터 처리 중** (`searchParams.get('next') ?? '/'`). → **callback 수정 불필요**
- **Supabase browser client** 팩토리: `@/lib/supabase/client` 의 `createClient()` — 기존 login/signup 폼이 동일 패턴 사용
- **Supabase Server Client** (`@/lib/supabase/server`) 는 route handler 전용 — reset 플로우에 불필요
- **`auth-provider.tsx`**: 세션 변경 감지 후 전역 상태 관리. 비밀번호 변경 후 세션 유지에 영향 없음 (동일 user, 비밀번호만 교체)
- **`minLength={6}`** 은 login-form/signup-form 공통 — reset-password 폼에도 동일 적용
- **다크모드 스타일**: `dark:bg-zinc-800`, `dark:text-zinc-100` 등 기존 클래스 그대로 재사용

### 1.4 Supabase 사전 조건 (사용자 수동)

- [x] Email Templates → Reset Password 적용 완료
- [ ] URL Configuration → Redirect URLs allowlist 확인:
  - `http://localhost:3000/**`
  - production/preview 도메인 `/**`

---

## 2. Architecture Overview

### 2.1 전체 플로우 (PKCE)

```
[로그인 페이지]
  "비밀번호를 잊으셨나요?" 링크 (신규)
       │
       ▼
[/auth/forgot-password] (신규)
  ForgotPasswordForm — email input
       │
       │ supabase.auth.resetPasswordForEmail(email, {
       │   redirectTo: `${origin}/auth/callback?next=/auth/reset-password`
       │ })
       ▼
  "메일 확인하세요" 안내 (result='sent')
       │
       │ (사용자가 메일 링크 클릭)
       ▼
[/auth/callback] (기존, 변경 없음)
  exchangeCodeForSession(code)  ← 활성 세션 생성
  redirect → next (= /auth/reset-password)
       │
       ▼
[/auth/reset-password] (신규)
  ResetPasswordForm
  ├─ useEffect: supabase.auth.getUser() → session 검증
  │    └─ 세션 없음 → "링크가 만료되었거나 유효하지 않습니다" 안내
  └─ onSubmit: supabase.auth.updateUser({ password })
       └─ 성공 → router.push('/') + router.refresh()
```

### 2.2 상태 머신

#### 2.2.1 `forgot-password-form` 상태

| 상태 | 진입 조건 | UI |
|---|---|---|
| `idle` | 초기 | email input + submit 버튼 |
| `loading` | 제출 직후 | submit 비활성, "처리 중..." |
| `sent` | Supabase 호출 성공 | 안내 박스 + "로그인으로" 링크 |
| `error` | Supabase 호출 에러 | 에러 메시지 표시, idle 로 복귀 가능 |

**주의**: Supabase `resetPasswordForEmail` 은 이메일 존재 여부와 무관하게 성공 응답 (보안 정책). `sent` 상태 항상 노출.

#### 2.2.2 `reset-password-form` 상태

| 상태 | 진입 조건 | UI |
|---|---|---|
| `checking` | 최초 마운트 | "세션 확인 중..." 스켈레톤 |
| `invalid` | `getUser()` 실패 또는 세션 없음 | "링크 만료/무효" + forgot-password 재요청 링크 |
| `ready` | 세션 확인 완료 | 비밀번호 입력 폼 |
| `loading` | submit 중 | submit 비활성, "처리 중..." |
| `error` | `updateUser` 실패 | 에러 메시지 + 재시도 가능 |
| (성공 시) | `updateUser` 성공 | `router.push('/')` — 컴포넌트 unmount |

### 2.3 불변 (Invariants)

- `/auth/callback/route.ts` **변경 없음**
- `login-form.tsx`, `signup-form.tsx`, `auth-provider.tsx` **변경 없음**
- `@/lib/supabase/{client,server}` **변경 없음**
- BE(`apps/api`) **변경 없음**
- DB 스키마 **변경 없음**

---

## 3. Component Designs

### 3.1 `apps/web/src/app/auth/forgot-password/page.tsx` (신규)

**책임**: 요청 페이지 shell — 레이아웃 + metadata + `ForgotPasswordForm` 마운트.

```tsx
import Link from 'next/link';

import { ForgotPasswordForm } from '@/features/auth/forgot-password-form';

import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: '비밀번호 재설정 — AI Stock Advisor',
};

export default function ForgotPasswordPage() {
  return (
    <main className="flex flex-1 items-center justify-center px-4 py-12">
      <div className="w-full max-w-sm space-y-6">
        <div className="text-center">
          <h1 className="text-xl font-semibold text-zinc-900 dark:text-zinc-100">
            비밀번호 재설정
          </h1>
          <p className="mt-1 text-sm text-zinc-500">
            가입하신 이메일을 입력하시면 재설정 링크를 보내드립니다.
          </p>
        </div>

        <ForgotPasswordForm />

        <p className="text-center text-sm text-zinc-500">
          <Link
            href="/auth/login"
            className="text-blue-600 hover:underline dark:text-blue-400"
          >
            로그인으로 돌아가기
          </Link>
        </p>
      </div>
    </main>
  );
}
```

### 3.2 `apps/web/src/features/auth/forgot-password-form.tsx` (신규)

**책임**: 이메일 수집 → `resetPasswordForEmail` 호출 → 상태 전이.

핵심 로직:
```tsx
'use client';

import { useState } from 'react';

import { createClient } from '@/lib/supabase/client';

export function ForgotPasswordForm() {
  const [email, setEmail] = useState('');
  const [status, setStatus] = useState<'idle' | 'loading' | 'sent' | 'error'>('idle');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage(null);
    setStatus('loading');

    const supabase = createClient();
    if (!supabase) {
      setErrorMessage('인증 서비스가 설정되지 않았습니다.');
      setStatus('error');
      return;
    }

    const { error } = await supabase.auth.resetPasswordForEmail(email, {
      redirectTo: `${window.location.origin}/auth/callback?next=/auth/reset-password`,
    });

    if (error) {
      setErrorMessage(error.message);
      setStatus('error');
      return;
    }

    setStatus('sent');
  };

  if (status === 'sent') {
    return (
      <div className="rounded-md border border-green-200 bg-green-50 p-4 text-center dark:border-green-800 dark:bg-green-950">
        <p className="text-sm font-medium text-green-800 dark:text-green-200">
          재설정 메일을 발송했습니다.
        </p>
        <p className="mt-1 text-xs text-green-600 dark:text-green-400">
          이메일의 링크를 클릭해 새 비밀번호를 설정해 주세요. 링크는 1시간 후 만료됩니다.
        </p>
      </div>
    );
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div>
        <label htmlFor="forgot-email" className="block text-sm font-medium text-zinc-700 dark:text-zinc-300">
          이메일
        </label>
        <input
          id="forgot-email"
          type="email"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          className="mt-1 block w-full rounded-md border border-zinc-300 bg-white px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500 dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-100"
          placeholder="you@example.com"
        />
      </div>

      {errorMessage && <p className="text-sm text-red-600 dark:text-red-400">{errorMessage}</p>}

      <button
        type="submit"
        disabled={status === 'loading'}
        className="w-full cursor-pointer rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50 dark:bg-blue-500 dark:hover:bg-blue-600"
      >
        {status === 'loading' ? '처리 중...' : '재설정 메일 보내기'}
      </button>
    </form>
  );
}
```

### 3.3 `apps/web/src/app/auth/reset-password/page.tsx` (신규)

**책임**: 재설정 페이지 shell — 세션 확인은 form 내부에서 수행.

```tsx
import { ResetPasswordForm } from '@/features/auth/reset-password-form';

import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: '새 비밀번호 설정 — AI Stock Advisor',
};

export default function ResetPasswordPage() {
  return (
    <main className="flex flex-1 items-center justify-center px-4 py-12">
      <div className="w-full max-w-sm space-y-6">
        <div className="text-center">
          <h1 className="text-xl font-semibold text-zinc-900 dark:text-zinc-100">
            새 비밀번호 설정
          </h1>
          <p className="mt-1 text-sm text-zinc-500">
            새 비밀번호를 입력해 주세요.
          </p>
        </div>

        <ResetPasswordForm />
      </div>
    </main>
  );
}
```

### 3.4 `apps/web/src/features/auth/reset-password-form.tsx` (신규)

**책임**: 세션 검증 → 비밀번호 수집/검증 → `updateUser` 호출 → 리다이렉트.

핵심 로직:
```tsx
'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';

import { createClient } from '@/lib/supabase/client';

type Status = 'checking' | 'invalid' | 'ready' | 'loading' | 'error';

export function ResetPasswordForm() {
  const router = useRouter();
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [status, setStatus] = useState<Status>('checking');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    const verifySession = async () => {
      const supabase = createClient();
      if (!supabase) {
        setErrorMessage('인증 서비스가 설정되지 않았습니다.');
        setStatus('invalid');
        return;
      }
      const { data, error } = await supabase.auth.getUser();
      if (error || !data.user) {
        setStatus('invalid');
        return;
      }
      setStatus('ready');
    };
    verifySession();
  }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage(null);

    if (password !== confirmPassword) {
      setErrorMessage('비밀번호가 일치하지 않습니다.');
      setStatus('error');
      return;
    }

    setStatus('loading');
    const supabase = createClient();
    if (!supabase) {
      setErrorMessage('인증 서비스가 설정되지 않았습니다.');
      setStatus('error');
      return;
    }

    const { error } = await supabase.auth.updateUser({ password });
    if (error) {
      setErrorMessage(error.message);
      setStatus('error');
      return;
    }

    router.push('/');
    router.refresh();
  };

  if (status === 'checking') {
    return (
      <div className="rounded-md border border-zinc-200 bg-zinc-50 p-4 text-center dark:border-zinc-700 dark:bg-zinc-900">
        <p className="text-sm text-zinc-600 dark:text-zinc-400">세션 확인 중...</p>
      </div>
    );
  }

  if (status === 'invalid') {
    return (
      <div className="space-y-4">
        <div className="rounded-md border border-red-200 bg-red-50 p-4 text-center dark:border-red-800 dark:bg-red-950">
          <p className="text-sm font-medium text-red-800 dark:text-red-200">
            링크가 만료되었거나 유효하지 않습니다.
          </p>
          <p className="mt-1 text-xs text-red-600 dark:text-red-400">
            재설정 메일을 다시 요청해 주세요.
          </p>
        </div>
        <Link
          href="/auth/forgot-password"
          className="block w-full rounded-md bg-blue-600 px-4 py-2 text-center text-sm font-medium text-white hover:bg-blue-700 dark:bg-blue-500 dark:hover:bg-blue-600"
        >
          재설정 메일 다시 요청하기
        </Link>
      </div>
    );
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div>
        <label htmlFor="reset-password" className="block text-sm font-medium text-zinc-700 dark:text-zinc-300">
          새 비밀번호
        </label>
        <input
          id="reset-password"
          type="password"
          required
          minLength={6}
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          className="mt-1 block w-full rounded-md border border-zinc-300 bg-white px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500 dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-100"
          placeholder="6자 이상 입력해주세요."
        />
      </div>

      <div>
        <label htmlFor="reset-confirm" className="block text-sm font-medium text-zinc-700 dark:text-zinc-300">
          비밀번호 확인
        </label>
        <input
          id="reset-confirm"
          type="password"
          required
          minLength={6}
          value={confirmPassword}
          onChange={(e) => setConfirmPassword(e.target.value)}
          className="mt-1 block w-full rounded-md border border-zinc-300 bg-white px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500 dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-100"
          placeholder="비밀번호 재입력"
        />
      </div>

      {errorMessage && <p className="text-sm text-red-600 dark:text-red-400">{errorMessage}</p>}

      <button
        type="submit"
        disabled={status === 'loading'}
        className="w-full cursor-pointer rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50 dark:bg-blue-500 dark:hover:bg-blue-600"
      >
        {status === 'loading' ? '변경 중...' : '비밀번호 변경'}
      </button>
    </form>
  );
}
```

### 3.5 `apps/web/src/app/auth/login/page.tsx` (수정)

**변경**: 비밀번호 입력 영역 아래, "회원가입" 링크 위에 "비밀번호를 잊으셨나요?" 링크 블록 추가.

```diff
         <LoginForm />

+        <p className="text-center text-sm">
+          <Link
+            href="/auth/forgot-password"
+            className="text-zinc-500 hover:text-zinc-700 hover:underline dark:text-zinc-400 dark:hover:text-zinc-200"
+          >
+            비밀번호를 잊으셨나요?
+          </Link>
+        </p>
+
         <div className="relative">
           <div className="absolute inset-0 flex items-center">
             <div className="w-full border-t border-zinc-200 dark:border-zinc-700" />
           </div>
```

---

## 4. Error Handling

### 4.1 에러 메시지 매핑

| Supabase 에러 | 발생 시점 | 사용자 표시 |
|---|---|---|
| `Invalid email` | forgot-password 제출 | "올바른 이메일 형식을 입력해 주세요." |
| `Email rate limit exceeded` | 짧은 시간 내 다수 요청 | "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요." |
| `New password should be different from the old password` | updateUser | "기존 비밀번호와 다른 비밀번호를 입력해 주세요." |
| `Password should be at least 6 characters` | updateUser | "비밀번호는 6자 이상이어야 합니다." |
| `Invalid token` / `expired` | updateUser (세션 만료) | "링크가 만료되었거나 유효하지 않습니다." (status=invalid) |
| 기타 | 전체 | `error.message` 원문 노출 (fallback) |

### 4.2 세션 검증 실패 UX

`reset-password-form` 의 `verifySession()` 실패 시 `status=invalid` → 에러 박스 + "재설정 메일 다시 요청하기" 버튼 노출. 사용자는 `/auth/forgot-password` 로 한 번의 클릭으로 복귀 가능.

### 4.3 네트워크 오류

Supabase SDK 가 throw 하는 경우는 드물지만, `try/catch` 대신 `{ error }` 반환 패턴 우선 사용 (기존 login/signup 과 일치).

---

## 5. UI/UX Design

### 5.1 화면 전환 흐름

```
/auth/login
   │  [비밀번호를 잊으셨나요?] ←── 신규 링크
   ▼
/auth/forgot-password
   │  email 입력
   ▼
[sent] "재설정 메일을 발송했습니다"
   │  (사용자 메일에서 링크 클릭)
   ▼
/auth/callback?code=...&next=/auth/reset-password  ← 기존 라우트
   ▼
/auth/reset-password
   │  세션 확인 → 신규 비밀번호 입력
   ▼
/ (홈, 로그인 상태 유지)
```

### 5.2 스타일 일관성

모든 신규 컴포넌트는 `login-form.tsx` / `signup-form.tsx` 의 Tailwind 클래스 세트 그대로 사용:

- Container: `flex flex-1 items-center justify-center px-4 py-12`
- Card: `w-full max-w-sm space-y-6`
- Input: `mt-1 block w-full rounded-md border border-zinc-300 bg-white px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500 dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-100`
- Primary button: `w-full cursor-pointer rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50 dark:bg-blue-500 dark:hover:bg-blue-600`
- Info box: `rounded-md border border-green-200 bg-green-50 ...`
- Error box: `rounded-md border border-red-200 bg-red-50 ...`

### 5.3 접근성

- `<label htmlFor>` 와 `<input id>` 매칭 (기존 패턴 유지)
- `required` + `type="email"` / `type="password"` 로 브라우저 검증 활용
- `autocomplete` 속성은 기존 폼에도 없음 — 일관성 유지 (추후 개선 별도 feature)

---

## 6. Security Considerations

- [x] **링크 1회성**: Supabase 가 기본 1시간 만료 + 1회 사용 토큰 발행 — FE 추가 처리 불필요
- [x] **이메일 존재 여부 노출 방지**: `resetPasswordForEmail` 은 항상 성공 응답 (Supabase 정책). FE 에서 분기 없음
- [x] **세션 탈취 방지**: PKCE flow — `code` 는 브라우저 cookie + PKCE verifier 로만 교환 가능 (Supabase SSR SDK 자동 처리)
- [x] **CSRF**: Supabase SDK 내부 처리 (state parameter + cookie)
- [x] **Rate limiting**: Supabase 측 SMTP rate limit (무료 SMTP 기준). FE 추가 throttle 불필요
- [ ] **비밀번호 복잡도**: MVP 범위 아님 (minLength=6 유지, 향후 강화 가능)
- [x] **세션 가드**: `reset-password` 진입 시 `getUser()` 확인으로 세션 없는 접근 차단
- [x] **HTTPS 강제**: production/preview 는 Vercel 자동 처리. 로컬은 http 허용

---

## 7. Test Plan

### 7.1 수동 테스트 시나리오 (필수)

| # | 시나리오 | 기대 결과 |
|---|---|---|
| T1 | 로그인 페이지 "비밀번호를 잊으셨나요?" 클릭 | `/auth/forgot-password` 이동 |
| T2 | 이메일 미입력 제출 | 브라우저 기본 검증으로 차단 |
| T3 | 존재하지 않는 이메일 제출 | `sent` 상태 표시 (보안 정책 — 존재 여부 비노출) |
| T4 | 존재하는 이메일 제출 → 메일 수신 확인 | Dashboard 템플릿 렌더링 + 한국어 제목/본문 |
| T5 | 메일 링크 클릭 | `/auth/callback` → `/auth/reset-password` 도달, `ready` 상태 |
| T6 | 비밀번호 5자 입력 | 브라우저 minLength 검증으로 차단 |
| T7 | 비밀번호/확인 불일치 | "비밀번호가 일치하지 않습니다" 표시 |
| T8 | 정상 비밀번호 입력 → 제출 | `updateUser` 성공 → 홈 이동 + 로그인 상태 유지 |
| T9 | 변경된 비밀번호로 재로그인 | 로그인 성공 |
| T10 | 기존(이전) 비밀번호로 로그인 시도 | 실패 ("이메일 또는 비밀번호가 올바르지 않습니다") |
| T11 | 만료된 링크(1시간+) 재사용 | `status=invalid` → "링크 만료" 안내 + 재요청 버튼 |
| T12 | `/auth/reset-password` 직접 접근 (세션 없음) | `status=invalid` → 재요청 버튼 |

### 7.2 자동 테스트

**Unit test 는 MVP 범위 아님** — FE form 컴포넌트 전반에 unit test 전략 없음 (login/signup 도 동일). `make web-check` 로 타입·린트 회귀만 검증.

### 7.3 회귀 검증

- [ ] 기존 login 플로우 정상 동작
- [ ] 기존 signup 플로우 정상 동작
- [ ] Google OAuth 정상 동작 (callback 핸들러 불변 검증)
- [ ] 다크모드 색상 일관성
- [ ] 모바일 뷰포트 (max-w-sm 기준)

---

## 8. Clean Architecture

### 8.1 Layer Assignment

| Component | Layer | Location |
|-----------|-------|----------|
| `ForgotPasswordPage` | Presentation | `src/app/auth/forgot-password/page.tsx` |
| `ResetPasswordPage` | Presentation | `src/app/auth/reset-password/page.tsx` |
| `ForgotPasswordForm` | Presentation | `src/features/auth/forgot-password-form.tsx` |
| `ResetPasswordForm` | Presentation | `src/features/auth/reset-password-form.tsx` |
| `createClient` (재사용) | Infrastructure | `src/lib/supabase/client.ts` |

**Application / Domain / 별도 서비스 추상화 없음** — Supabase SDK 가 Application-level API (`resetPasswordForEmail`, `updateUser`) 를 직접 제공하므로 중간 레이어 불필요 (기존 auth flows 과 동일 철학).

### 8.2 Import Order (예시)

```typescript
// 1. External
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';

// 2. Internal absolute
import { createClient } from '@/lib/supabase/client';

// 3. Relative (없음)

// 4. Types (필요 시)
import type { Metadata } from 'next';
```

---

## 9. Coding Convention Reference

### 9.1 Naming

| Target | Rule | 본 feature 적용 |
|---|---|---|
| Component | PascalCase | `ForgotPasswordForm`, `ResetPasswordForm` |
| Hook | camelCase | (사용 없음) |
| File | kebab-case | `forgot-password-form.tsx`, `reset-password-form.tsx` |
| Folder | kebab-case | `auth/forgot-password/`, `auth/reset-password/` |
| Next.js 예약 파일 | 프레임워크 규칙 | `page.tsx` |

### 9.2 Import Order — 위 §8.2 참조

### 9.3 Environment Variables

**신규 env 없음.** 기존 `NEXT_PUBLIC_SUPABASE_URL`, `NEXT_PUBLIC_SUPABASE_ANON_KEY` 재사용.

### 9.4 CLAUDE.md 준수 체크리스트

- [x] FE 파일명 kebab-case (Next.js 예약 파일 예외)
- [x] Tailwind 4 클래스 사용
- [x] Next.js 16 App Router 패턴
- [x] React 19 함수 컴포넌트
- [x] `@/` 절대 경로 import
- [x] 투자 자문 아님 원칙 — auth 범위 밖이라 추가 문구 불필요

---

## 10. Implementation Guide

### 10.1 File Structure

```
apps/web/src/
├── app/auth/
│   ├── login/
│   │   └── page.tsx                  ← 수정 (링크 추가)
│   ├── signup/
│   │   └── page.tsx                  (변경 없음)
│   ├── callback/
│   │   └── route.ts                  (변경 없음)
│   ├── forgot-password/              ← 신규 폴더
│   │   └── page.tsx                  ← 신규
│   └── reset-password/               ← 신규 폴더
│       └── page.tsx                  ← 신규
└── features/auth/
    ├── login-form.tsx                (변경 없음)
    ├── signup-form.tsx               (변경 없음)
    ├── social-login.tsx              (변경 없음)
    ├── auth-provider.tsx             (변경 없음)
    ├── forgot-password-form.tsx      ← 신규
    └── reset-password-form.tsx       ← 신규
```

### 10.2 Implementation Order

1. [ ] **Step 1**: `feat/password-reset` 브랜치 생성 (develop 에서 분기)
2. [ ] **Step 2**: `features/auth/forgot-password-form.tsx` 작성
3. [ ] **Step 3**: `app/auth/forgot-password/page.tsx` 작성
4. [ ] **Step 4**: `features/auth/reset-password-form.tsx` 작성
5. [ ] **Step 5**: `app/auth/reset-password/page.tsx` 작성
6. [ ] **Step 6**: `app/auth/login/page.tsx` 에 링크 추가
7. [ ] **Step 7**: `make web-check` 통과 확인 (tsc + eslint)
8. [ ] **Step 8**: `make web-build` 통과 확인 (빌드 에러 없음)
9. [ ] **Step 9**: 커밋 + 푸시 + PR 생성 (squash merge target = develop)
10. [ ] **Step 10**: `/pdca analyze password-reset` Gap 분석
11. [ ] **Step 11**: Match Rate ≥ 90% 시 `/pdca report` → `/pdca archive`

### 10.3 Verification Commands

```bash
# 타입/린트 검증
make web-check

# 빌드 검증
make web-build

# 로컬 실행 (수동 테스트용)
make web-dev
```

### 10.4 Commit Convention

```
feat(auth): password-reset — forgot/reset 페이지 + 로그인 링크

- /auth/forgot-password: resetPasswordForEmail 호출 페이지
- /auth/reset-password: 세션 확인 + updateUser({ password })
- /auth/login: "비밀번호를 잊으셨나요?" 링크 추가
- BE 변경 없음, Supabase Auth 표준 플로우 재사용
- 기존 /auth/callback route 재사용 (next=/auth/reset-password)
```

---

## 11. Open Questions (없음)

설계 단계에서 해결되지 않은 질문 없음. Plan 단계의 FR-10(이미 로그인된 사용자 접근 시 리다이렉트)은 **구현 생략** 결정 — 사용 빈도 극히 낮고 본인 계정 변경은 의도적 행위로 간주.

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 0.1 | 2026-04-20 | Initial draft | wonseok-han |
