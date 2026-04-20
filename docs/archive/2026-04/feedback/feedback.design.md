---
template: design
version: 1.0
feature: feedback
date: 2026-04-20
author: wonseok-han
project: AI Stock Advisor
status: Draft
plan: ../../01-plan/features/feedback.plan.md
---

# feedback Design

## Executive Summary

| 관점 | 요약 |
|---|---|
| **Problem** | 베타 피드백 채널 부재로 버그 신고·문의 수집 경로 단절. |
| **Solution** | `/feedback` 페이지 → 클라이언트에서 Supabase `public.feedback` 테이블에 직접 INSERT. RLS 로 INSERT 공개·SELECT 서비스 롤 only. 스키마는 Flyway V14 로 버전 관리. |
| **Function UX Effect** | 푸터 링크 → 유형·제목·본문·이메일 입력 → 제출 → 감사 안내. 로그인 시 `user_id`/email 자동 주입. |
| **Core Value** | 자체 구현으로 브랜드·면책 일관성, 관리자 UI 0 (Supabase Dashboard 재사용), 허니팟+쿨다운+길이 제한으로 스팸 MVP 방어. |

## 1. Overview / Scope

### 1.1 범위 (신규 4개 + 수정 2개)

| 구분 | 파일 | 역할 |
|---|---|---|
| 신규 | `apps/web/src/app/feedback/page.tsx` | 페이지 (metadata + layout) |
| 신규 | `apps/web/src/features/feedback/feedback-form.tsx` | 폼 client 컴포넌트 (상태머신·검증·제출) |
| 신규 | `apps/web/src/features/feedback/types.ts` | `FeedbackType`, `FeedbackInsert` 타입 |
| 신규 | `apps/api/src/main/resources/db/migration/V14__feedback.sql` | 테이블 + RLS 정책 (Flyway) |
| 수정 | `apps/web/src/components/legal/disclaimer-footer.tsx` | "피드백 보내기" 링크 추가 |
| 수정 | (옵션) `apps/web/src/components/layout/site-header.tsx` | 현재는 미수정 — 푸터만으로 충분 |

### 1.2 불변 영역 (수정 금지)

- `apps/api/src/main/java/**/*.java` — Java 코드 0 변경
- `apps/api/src/main/resources/application*.yml`
- `@/lib/supabase/{client,server}.ts`
- `@/features/auth/*`
- 기타 Phase 1~4.5 feature 파일 전부

### 1.3 Plan 대비 변경

| 항목 | Plan | Design (최종) | 사유 |
|---|---|---|---|
| 마이그레이션 위치 | `supabase/migrations/` 또는 design 블록 | **Flyway V14 (`apps/api/src/main/resources/db/migration/`)** | 기존 `users`/`bookmarks`/`notification_settings` 모두 Flyway 로 관리 — 스키마 일관성 유지 |
| BE 변경 | 0 | SQL 파일 1개 추가 (Java 코드·엔드포인트 0) | Flyway 마이그레이션은 런타임 동작·API 에 영향 없음 |

## 2. Architecture

### 2.1 플로우

```
(사용자)
  │
  ├── 푸터 "피드백 보내기" 클릭
  │      │
  │      ▼
  │   /feedback (page.tsx)
  │      │
  │      ▼
  │   FeedbackForm (client)
  │      │ 1. auth-provider 세션 구독 → user, email prefill
  │      │ 2. 유형/제목/본문/이메일 입력
  │      │ 3. 쿨다운 확인 (localStorage)
  │      │ 4. 허니팟 검사
  │      │ 5. supabase.from('feedback').insert(...)
  │      │
  │      └── 성공 → 'sent' 상태 + 감사 안내
  │          실패 → 'error' 상태 + 한국어 메시지
  │
  └── (관리자: Supabase Dashboard SQL Editor 로 별도 조회)
```

### 2.2 상태 머신

```
 FeedbackForm 상태
 ─────────────────────────
 idle ──(submit)──► loading ──(insert ok)──► sent
                       │
                       └──(insert err)──► error ──(retry)──► loading
                       │
                       └──(cooldown active)──► idle (안내만 갱신)
                       │
                       └──(honeypot filled)──► sent (조용히 무시)

 초기 진입 시:
   - session? → user_id + email prefill + email readOnly
   - !session → email required, 수기 입력
```

### 2.3 파일 의존 그래프

```
app/feedback/page.tsx
  └─ features/feedback/feedback-form.tsx
       ├─ features/auth/auth-provider.tsx (useAuth)
       ├─ lib/supabase/client.ts (createClient)
       └─ features/feedback/types.ts (FeedbackType, FeedbackInsert)

components/legal/disclaimer-footer.tsx
  └─ <Link href="/feedback"> (추가)
```

## 3. DB Schema

### 3.1 Flyway V14 마이그레이션

`apps/api/src/main/resources/db/migration/V14__feedback.sql`:

```sql
-- V14: feedback 테이블 + RLS 정책
-- 베타 피드백 수집 채널 (버그/문의/제안).
-- INSERT 는 익명·인증 모두 허용, SELECT/UPDATE/DELETE 는 service_role only.

CREATE TABLE IF NOT EXISTS public.feedback (
    id          BIGSERIAL PRIMARY KEY,
    user_id     UUID        REFERENCES auth.users(id) ON DELETE SET NULL,
    email       VARCHAR(255),
    type        VARCHAR(32) NOT NULL CHECK (type IN ('bug', 'question', 'suggestion')),
    subject     VARCHAR(200) NOT NULL,
    body        TEXT        NOT NULL,
    url         VARCHAR(500),
    user_agent  VARCHAR(500),
    status      VARCHAR(32) NOT NULL DEFAULT 'open'
                CHECK (status IN ('open', 'in_progress', 'resolved', 'closed')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_feedback_created_at ON public.feedback(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_feedback_status     ON public.feedback(status);
CREATE INDEX IF NOT EXISTS idx_feedback_type       ON public.feedback(type);

-- RLS 활성화
ALTER TABLE public.feedback ENABLE ROW LEVEL SECURITY;

-- INSERT 정책: anon + authenticated 모두 허용
DROP POLICY IF EXISTS "feedback_insert_public" ON public.feedback;
CREATE POLICY "feedback_insert_public" ON public.feedback
    FOR INSERT
    TO anon, authenticated
    WITH CHECK (true);

-- SELECT/UPDATE/DELETE: service_role 만 허용 (RLS 통과 못하면 자동 차단)
-- 관리자 작업은 Supabase Dashboard (service_role) 에서만 수행.
DROP POLICY IF EXISTS "feedback_read_service" ON public.feedback;
CREATE POLICY "feedback_read_service" ON public.feedback
    FOR SELECT
    TO service_role
    USING (true);

DROP POLICY IF EXISTS "feedback_update_service" ON public.feedback;
CREATE POLICY "feedback_update_service" ON public.feedback
    FOR UPDATE
    TO service_role
    USING (true)
    WITH CHECK (true);

DROP POLICY IF EXISTS "feedback_delete_service" ON public.feedback;
CREATE POLICY "feedback_delete_service" ON public.feedback
    FOR DELETE
    TO service_role
    USING (true);

COMMENT ON TABLE public.feedback IS '베타 피드백 수집 (버그/문의/제안) — INSERT 공개, 조회/수정은 service_role 전용';
```

### 3.2 컬럼 설계 근거

| 컬럼 | 타입 | 비고 |
|---|---|---|
| `id` | BIGSERIAL | 순차 생성 PK |
| `user_id` | UUID NULLABLE | 로그인 시만 채움. `auth.users(id)` 참조, 계정 삭제 시 SET NULL (히스토리 보존) |
| `email` | VARCHAR(255) | 익명 제출 시 필수, 로그인 시 auto-fill. DB 레벨 NOT NULL 은 아님 (허니팟 우회 대응 여유) |
| `type` | CHECK 제약 | `bug` / `question` / `suggestion` 만 허용 |
| `subject` | VARCHAR(200) NOT NULL | 1~100 자 FE 검증 + DB 200 여유 |
| `body` | TEXT NOT NULL | 10~2000 자 FE 검증 |
| `url` | VARCHAR(500) | 제출 당시 페이지 URL |
| `user_agent` | VARCHAR(500) | 재현 디버깅 단서 |
| `status` | CHECK 제약 | Dashboard 에서 수동 변경용. 기본 `open` |
| `created_at` / `updated_at` | TIMESTAMPTZ | 자동 타임스탬프 |

## 4. 파일별 구현

### 4.1 `features/feedback/types.ts`

```ts
/**
 * 피드백 유형.
 * DB CHECK 제약과 동일.
 */
export type FeedbackType = 'bug' | 'question' | 'suggestion';

export const FEEDBACK_TYPE_LABELS: Record<FeedbackType, string> = {
  bug: '버그 신고',
  question: '문의',
  suggestion: '제안',
};

/**
 * Supabase insert payload.
 * DB 스키마의 subset — status/created_at/updated_at 는 자동.
 */
export interface FeedbackInsert {
  user_id: string | null;
  email: string;
  type: FeedbackType;
  subject: string;
  body: string;
  url: string | null;
  user_agent: string | null;
}

export const FEEDBACK_LIMITS = {
  SUBJECT_MIN: 1,
  SUBJECT_MAX: 100,
  BODY_MIN: 10,
  BODY_MAX: 2000,
  COOLDOWN_MS: 60_000, // 60초
} as const;

export const FEEDBACK_COOLDOWN_KEY = 'feedback:lastSubmittedAt';
```

### 4.2 `features/feedback/feedback-form.tsx`

```tsx
'use client';

import Link from 'next/link';
import { useEffect, useRef, useState } from 'react';

import { useAuth } from '@/features/auth/auth-provider';
import { createClient } from '@/lib/supabase/client';

import {
  FEEDBACK_COOLDOWN_KEY,
  FEEDBACK_LIMITS,
  FEEDBACK_TYPE_LABELS,
  type FeedbackInsert,
  type FeedbackType,
} from './types';

type Status = 'idle' | 'loading' | 'sent' | 'error';

/**
 * 피드백 제출 폼.
 * 유형/제목/본문/이메일 입력 → Supabase `feedback` 테이블 INSERT.
 * 로그인 시 user_id + email 자동 주입, 비로그인 시 email 수기 입력.
 * 허니팟 + 60초 쿨다운 + 길이 제한으로 스팸 방어.
 */
export function FeedbackForm() {
  const { user, isLoading: authLoading } = useAuth();
  const [type, setType] = useState<FeedbackType>('bug');
  const [subject, setSubject] = useState('');
  const [body, setBody] = useState('');
  const [email, setEmail] = useState('');
  const [status, setStatus] = useState<Status>('idle');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [cooldownLeft, setCooldownLeft] = useState(0);
  const honeypotRef = useRef<HTMLInputElement>(null);

  // 로그인 사용자 email prefill
  useEffect(() => {
    if (!authLoading && user?.email) {
      setEmail(user.email);
    }
  }, [authLoading, user?.email]);

  // 쿨다운 잔여 시간 계산 (초기 + 1초마다 업데이트)
  useEffect(() => {
    const tick = () => {
      const raw = localStorage.getItem(FEEDBACK_COOLDOWN_KEY);
      const last = raw ? parseInt(raw, 10) : 0;
      const elapsed = Date.now() - last;
      const left = Math.max(0, FEEDBACK_LIMITS.COOLDOWN_MS - elapsed);
      setCooldownLeft(Math.ceil(left / 1000));
    };
    tick();
    const id = setInterval(tick, 1000);
    return () => clearInterval(id);
  }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage(null);

    // 허니팟: 채워졌으면 조용히 sent 처리
    if (honeypotRef.current?.value) {
      setStatus('sent');
      return;
    }

    // 쿨다운
    if (cooldownLeft > 0) {
      setErrorMessage(`${cooldownLeft}초 후 다시 시도해 주세요.`);
      setStatus('error');
      return;
    }

    // 길이 검증 (브라우저 minLength 보강)
    if (subject.trim().length < FEEDBACK_LIMITS.SUBJECT_MIN) {
      setErrorMessage('제목을 입력해 주세요.');
      setStatus('error');
      return;
    }
    if (body.trim().length < FEEDBACK_LIMITS.BODY_MIN) {
      setErrorMessage(`본문을 ${FEEDBACK_LIMITS.BODY_MIN}자 이상 작성해 주세요.`);
      setStatus('error');
      return;
    }

    setStatus('loading');
    const supabase = createClient();
    if (!supabase) {
      setErrorMessage('피드백 서비스가 일시적으로 사용 불가능합니다.');
      setStatus('error');
      return;
    }

    const payload: FeedbackInsert = {
      user_id: user?.id ?? null,
      email: email.trim(),
      type,
      subject: subject.trim(),
      body: body.trim(),
      url: typeof window !== 'undefined' ? window.location.href : null,
      user_agent: typeof window !== 'undefined' ? window.navigator.userAgent : null,
    };

    const { error } = await supabase.from('feedback').insert(payload);

    if (error) {
      setErrorMessage(mapErrorMessage(error.message));
      setStatus('error');
      return;
    }

    localStorage.setItem(FEEDBACK_COOLDOWN_KEY, Date.now().toString());
    setStatus('sent');
  };

  if (status === 'sent') {
    return (
      <div className="space-y-4">
        <div className="rounded-md border border-green-200 bg-green-50 p-4 text-center dark:border-green-800 dark:bg-green-950">
          <p className="text-sm font-medium text-green-800 dark:text-green-200">
            피드백을 받았습니다. 감사합니다.
          </p>
          <p className="mt-1 text-xs text-green-600 dark:text-green-400">
            확인 후 필요 시 입력해 주신 이메일로 답변 드리겠습니다.
          </p>
        </div>
        <Link
          href="/"
          className="block w-full rounded-md bg-blue-600 px-4 py-2 text-center text-sm font-medium text-white hover:bg-blue-700 dark:bg-blue-500 dark:hover:bg-blue-600"
        >
          홈으로 돌아가기
        </Link>
      </div>
    );
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4" noValidate>
      {/* 허니팟 (봇 탐지용, 사용자에겐 숨김) */}
      <input
        ref={honeypotRef}
        type="text"
        name="company"
        tabIndex={-1}
        autoComplete="off"
        className="sr-only"
        aria-hidden="true"
      />

      <div>
        <label htmlFor="feedback-type" className="block text-sm font-medium text-zinc-700 dark:text-zinc-300">
          유형
        </label>
        <select
          id="feedback-type"
          value={type}
          onChange={(e) => setType(e.target.value as FeedbackType)}
          className="mt-1 block w-full rounded-md border border-zinc-300 bg-white px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500 dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-100"
        >
          <option value="bug">{FEEDBACK_TYPE_LABELS.bug}</option>
          <option value="question">{FEEDBACK_TYPE_LABELS.question}</option>
          <option value="suggestion">{FEEDBACK_TYPE_LABELS.suggestion}</option>
        </select>
      </div>

      <div>
        <label htmlFor="feedback-subject" className="block text-sm font-medium text-zinc-700 dark:text-zinc-300">
          제목
        </label>
        <input
          id="feedback-subject"
          type="text"
          required
          maxLength={FEEDBACK_LIMITS.SUBJECT_MAX}
          value={subject}
          onChange={(e) => setSubject(e.target.value)}
          className="mt-1 block w-full rounded-md border border-zinc-300 bg-white px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500 dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-100"
          placeholder="한 줄 요약"
        />
      </div>

      <div>
        <label htmlFor="feedback-body" className="block text-sm font-medium text-zinc-700 dark:text-zinc-300">
          내용
        </label>
        <textarea
          id="feedback-body"
          required
          minLength={FEEDBACK_LIMITS.BODY_MIN}
          maxLength={FEEDBACK_LIMITS.BODY_MAX}
          value={body}
          onChange={(e) => setBody(e.target.value)}
          rows={8}
          className="mt-1 block w-full rounded-md border border-zinc-300 bg-white px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500 dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-100"
          placeholder={`재현 단계, 기대 동작, 실제 동작 등 구체적으로 작성해 주세요. (${FEEDBACK_LIMITS.BODY_MIN}~${FEEDBACK_LIMITS.BODY_MAX}자)`}
        />
        <p className="mt-1 text-xs text-zinc-500 dark:text-zinc-400">
          {body.length} / {FEEDBACK_LIMITS.BODY_MAX}
        </p>
      </div>

      <div>
        <label htmlFor="feedback-email" className="block text-sm font-medium text-zinc-700 dark:text-zinc-300">
          이메일 {user?.email ? '(로그인 계정)' : ''}
        </label>
        <input
          id="feedback-email"
          type="email"
          required
          readOnly={!!user?.email}
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          className="mt-1 block w-full rounded-md border border-zinc-300 bg-white px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500 read-only:bg-zinc-100 dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-100 dark:read-only:bg-zinc-900"
          placeholder="you@example.com"
        />
      </div>

      {errorMessage && (
        <p className="text-sm text-red-600 dark:text-red-400">{errorMessage}</p>
      )}

      <button
        type="submit"
        disabled={status === 'loading' || cooldownLeft > 0}
        className="w-full cursor-pointer rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50 dark:bg-blue-500 dark:hover:bg-blue-600"
      >
        {status === 'loading'
          ? '전송 중...'
          : cooldownLeft > 0
            ? `${cooldownLeft}초 후 재시도 가능`
            : '피드백 보내기'}
      </button>

      <p className="text-xs text-zinc-500 dark:text-zinc-400">
        본 서비스는 투자 자문이 아닙니다. 투자 판단과 그에 따른 책임은 사용자 본인에게 있으며,
        개별 투자 자문 요청에는 응답하지 않습니다.
      </p>
    </form>
  );
}

function mapErrorMessage(message: string): string {
  const lower = message.toLowerCase();
  if (lower.includes('row-level security') || lower.includes('rls')) {
    return '피드백 저장에 실패했습니다. 잠시 후 다시 시도해 주세요.';
  }
  if (lower.includes('check constraint')) {
    return '입력 값이 유효하지 않습니다. 유형/내용을 확인해 주세요.';
  }
  if (lower.includes('violates') && lower.includes('foreign key')) {
    return '세션이 만료되었습니다. 다시 로그인 후 시도해 주세요.';
  }
  if (lower.includes('network') || lower.includes('failed to fetch')) {
    return '네트워크 연결을 확인해 주세요.';
  }
  return '일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.';
}
```

### 4.3 `app/feedback/page.tsx`

```tsx
import { FeedbackForm } from '@/features/feedback/feedback-form';

import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: '피드백 보내기 — AI Stock Advisor',
  description: '버그 신고, 문의, 제안을 보내주세요.',
};

export default function FeedbackPage() {
  return (
    <main className="flex flex-1 items-start justify-center px-4 py-12">
      <div className="w-full max-w-xl space-y-6">
        <div>
          <h1 className="text-xl font-semibold text-zinc-900 dark:text-zinc-100">
            피드백 보내기
          </h1>
          <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">
            버그를 발견하셨거나 개선 아이디어가 있으시면 알려주세요. 베타 단계라 모든 의견이 큰 도움이 됩니다.
          </p>
        </div>

        <FeedbackForm />
      </div>
    </main>
  );
}
```

### 4.4 `components/legal/disclaimer-footer.tsx` (수정)

기존 `<nav aria-label="법적 고지">` 내 링크 목록 뒤에 **"피드백 보내기"** 링크 추가:

```tsx
<nav className="flex flex-wrap gap-4" aria-label="법적 고지">
  <Link href="/legal/disclaimer" className="hover:underline">면책 고지</Link>
  <Link href="/legal/terms" className="hover:underline">이용약관</Link>
  <Link href="/legal/privacy" className="hover:underline">개인정보 처리방침</Link>
  <Link href="/feedback" className="hover:underline">피드백 보내기</Link>
</nav>
```

## 5. 에러 매핑 (`mapErrorMessage`)

| Supabase 에러 키워드 | 한국어 메시지 | 원인 |
|---|---|---|
| `row-level security` / `rls` | 피드백 저장에 실패했습니다. 잠시 후 다시 시도해 주세요. | RLS 정책 위반 (인증 상태 변화 등) |
| `check constraint` | 입력 값이 유효하지 않습니다. 유형/내용을 확인해 주세요. | `type` / `status` CHECK 위배 |
| `violates foreign key` | 세션이 만료되었습니다. 다시 로그인 후 시도해 주세요. | `user_id` 가 `auth.users` 에 없을 때 (희귀) |
| `network` / `failed to fetch` | 네트워크 연결을 확인해 주세요. | 오프라인/타임아웃 |
| 그 외 | 일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요. | fallback |

## 6. 보안

| 레이어 | 방어 | 구현 |
|---|---|---|
| **프론트 (UX)** | 허니팟 필드 `name="company"` sr-only | 채워지면 조용히 `sent` 처리 (봇 패배감 미노출) |
| **프론트 (UX)** | 60초 쿨다운 (localStorage) | 제출 성공 시 timestamp 저장, 이후 버튼 비활성 + 잔여 시간 표시 |
| **프론트 (검증)** | 길이 제한 (subject ≤100, body 10~2000) | `maxLength` + 수동 `.length` 체크 |
| **DB (RLS)** | INSERT 공개, SELECT/UPDATE/DELETE service_role only | V14 SQL 정책 |
| **DB (제약)** | `type` / `status` CHECK | 허용 값 외 INSERT 실패 |
| **PII 최소화** | 이메일·user_id 외 저장 금지 | 이름·전화 컬럼 없음 |
| **계정 삭제** | user_id `ON DELETE SET NULL` | 탈퇴 시 피드백 히스토리 보존, 식별자만 제거 |

**RLS 우회 시나리오**:
- 공격자가 익명 client 로 SELECT 시도 → RLS 로 0 row 반환 (권한 없음)
- Supabase anon key 는 브라우저 노출되지만, service_role key 는 절대 브라우저에 노출 금지 (기존 `@/lib/env` 구성 그대로)

## 7. 테스트 (수동)

| # | 시나리오 | 기대 |
|---|---|---|
| T1 | 비로그인 상태 `/feedback` 진입 | email 수기 입력 활성 |
| T2 | 로그인 상태 `/feedback` 진입 | email 자동 채움 + readOnly |
| T3 | 유형=버그, 제목·본문 정상 입력 → 제출 | sent 화면 + `feedback` 테이블 row 1건 생성 |
| T4 | 본문 9자 이하 제출 시도 | "본문을 10자 이상" 에러 |
| T5 | 연속 제출 (60초 내) | "N초 후 재시도 가능" 버튼 비활성 |
| T6 | 허니팟 수동으로 값 넣고 제출 (개발자 도구) | 화면은 sent 전환, 실제 DB insert 없음 |
| T7 | 오프라인 상태에서 제출 | "네트워크 연결을 확인해 주세요" |
| T8 | Supabase Dashboard SQL Editor 에서 `SELECT * FROM public.feedback;` | 제출 row 조회 가능 (service_role) |
| T9 | 다크 모드 | 색 전환 정상 |
| T10 | 푸터 링크 클릭 | `/feedback` 이동 |
| T11 | 모바일 뷰 (Safari iOS) | max-w-xl 내 스크롤 + 터치 정상 |
| T12 | 제출 성공 후 홈 이동 버튼 | `/` 로 네비게이션 |

## 8. 레이어 (Clean Architecture)

| 파일 | Layer | 역할 |
|---|---|---|
| `app/feedback/page.tsx` | Presentation (server) | metadata + layout |
| `features/feedback/feedback-form.tsx` | Presentation (client) | 상태머신·검증·UX |
| `features/feedback/types.ts` | Domain | 타입·상수 (불변) |
| `lib/supabase/client.ts` | Infrastructure | Supabase SDK 어댑터 (재사용) |
| `features/auth/auth-provider.tsx` | Infrastructure | 세션 구독 (재사용) |
| `V14__feedback.sql` | Persistence | 스키마 + RLS |

Application 레이어(UseCase) 분리는 폼 로직이 단일 UI 컨텍스트에 닫혀 있어 생략 (password-reset 과 동일 판단).

## 9. 컨벤션

### 9.1 Naming
- Component: `FeedbackForm` (PascalCase)
- File: `feedback-form.tsx` (kebab-case)
- Type: `FeedbackType`, `FeedbackInsert` (PascalCase)
- Constant: `FEEDBACK_LIMITS`, `FEEDBACK_COOLDOWN_KEY` (UPPER_SNAKE_CASE)
- Function: `mapErrorMessage` (camelCase)

### 9.2 Import Order
1. 외부 (`next/link`, `react`)
2. 절대경로 (`@/...`)
3. 상대경로 (`./types`)
4. `import type`
5. 스타일 (해당 없음)

### 9.3 Directory
```
apps/web/src/
├─ app/feedback/page.tsx          (신규)
├─ features/feedback/              (신규 폴더)
│   ├─ feedback-form.tsx
│   └─ types.ts
└─ components/legal/disclaimer-footer.tsx  (수정)

apps/api/src/main/resources/db/migration/
└─ V14__feedback.sql                (신규)
```

## 10. 구현 순서

### 10.1 Step 순서 (Do phase)

1. **S1** — `types.ts` 작성
2. **S2** — `feedback-form.tsx` 작성 (imports, 상태 초기화, useEffect, handleSubmit, JSX)
3. **S3** — `app/feedback/page.tsx` 작성
4. **S4** — `disclaimer-footer.tsx` 에 링크 추가
5. **S5** — `V14__feedback.sql` 작성
6. **S6** — `make web-check` 통과 확인
7. **S7** — `make web-build` 통과 확인
8. **S8** — (수동 DB 적용은 PR 머지 후, Supabase Dashboard 또는 로컬 Flyway 재기동으로 처리)
9. **S9** — git add + commit + push + PR 생성
10. **S10** — `/pdca analyze feedback` 로 Gap 분석

### 10.2 체크리스트 (커밋 전)

- [ ] 5개 파일 모두 존재
- [ ] `make web-check` 0 errors
- [ ] `make web-build` 성공, `/feedback` route Static 등록 확인
- [ ] 허니팟·쿨다운·길이 제한 3중 방어 모두 구현
- [ ] 투자 자문 면책 문구 폼 하단에 표시
- [ ] 다크모드 클래스 적용

### 10.3 커밋 메시지

```
feat(feedback): 베타 피드백 채널 — /feedback 페이지 + Supabase 테이블

- /feedback: 유형/제목/본문/이메일 입력 폼 (허니팟 + 60s 쿨다운 + 길이 제한)
- features/feedback/: FeedbackForm client 컴포넌트 + 타입/상수
- V14__feedback.sql: public.feedback 테이블 + RLS (INSERT 공개, SELECT/UPDATE/DELETE service_role)
- disclaimer-footer: "피드백 보내기" 링크 추가
- 로그인 시 user_id/email 자동 주입, 비로그인도 수기 제출 가능
- BE Java 0 변경, 관리자 UI 0 (Supabase Dashboard 재사용)
```

### 10.4 PR

- 브랜치: `feat/feedback`
- 베이스: `develop`
- squash merge

## 11. 설계 결정 (Key Decisions)

| # | 결정 | 대안 | 근거 |
|---|---|---|---|
| D1 | Supabase 테이블 직접 INSERT | Spring 엔드포인트 신설 | FE-only 완결로 1인 개발 리소스 최소. RLS 로 보안 충분. |
| D2 | Flyway V14 | `supabase/migrations/` 신규 디렉터리 / 수동 Dashboard | 기존 `users`/`bookmarks`/`notification_settings` 전부 Flyway — 스키마 일관성 우선 |
| D3 | 관리자 UI 미구현 | `/admin/feedback` 페이지 | Supabase Dashboard 로 갈음, 베타 트래픽 규모 대비 ROI 낮음 |
| D4 | 허니팟 + 쿨다운 (hCaptcha X) | hCaptcha / Turnstile | 베타 트래픽 기준 과도. 실사용 로그로 필요 시 후속 추가 |
| D5 | 익명 제출 허용 | 로그인 필수 | 비로그인 사용자의 신고 접근성 (특히 회원가입 중 문제 겪은 사용자) 보장 |
| D6 | `user_id` ON DELETE SET NULL | CASCADE | 계정 탈퇴 시 피드백 히스토리 보존 (운영상 유용) |
| D7 | 푸터 단일 진입점 | 헤더 + 마이페이지 병행 | MVP 범위 최소화, 필요 시 후속 확장 |
| D8 | 허니팟 트리거 시 조용히 sent | 에러 표시 | 봇에게 탐지 사실 미노출 (defense-in-depth) |

## 12. 후속 (Out of scope, 향후 가능)

- hCaptcha / Cloudflare Turnstile 통합 (R2 대응)
- 제출 시 사용자 확인 이메일 발송 (SMTP)
- `/admin/feedback` 관리자 페이지 (상태 변경, 응답 기록)
- Slack/Discord 웹훅 (신규 피드백 실시간 알림)
- 피드백 본문 내 욕설/스팸 자동 필터 (`LegalGuardFilter` 재사용 가능성 검토)
- Supabase Storage 첨부 파일 (스크린샷) 지원
