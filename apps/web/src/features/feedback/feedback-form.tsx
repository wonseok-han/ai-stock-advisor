'use client';

import Link from 'next/link';
import { useEffect, useRef, useState } from 'react';

import { useAuth } from '@/features/auth/auth-provider';
import { apiFetch } from '@/lib/api/client';

import {
  FEEDBACK_COOLDOWN_KEY,
  FEEDBACK_LIMITS,
  FEEDBACK_TYPE_LABELS,
  type FeedbackType,
} from './types';

type Status = 'idle' | 'loading' | 'sent' | 'error';

export function FeedbackForm() {
  const { user } = useAuth();
  const [type, setType] = useState<FeedbackType>('bug');
  const [subject, setSubject] = useState('');
  const [body, setBody] = useState('');
  const [emailInput, setEmailInput] = useState('');
  const [status, setStatus] = useState<Status>('idle');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [cooldownLeft, setCooldownLeft] = useState(0);
  const honeypotRef = useRef<HTMLInputElement>(null);

  const email = user?.email ?? emailInput;

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

    if (honeypotRef.current?.value) {
      setStatus('sent');
      return;
    }

    if (cooldownLeft > 0) {
      setErrorMessage(`${cooldownLeft}초 후 다시 시도해 주세요.`);
      setStatus('error');
      return;
    }

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

    try {
      await apiFetch('/feedback', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          userId: user?.id ?? null,
          type,
          subject: subject.trim(),
          body: body.trim(),
          email: email.trim(),
          url: typeof window !== 'undefined' ? window.location.href : null,
          userAgent: typeof window !== 'undefined' ? window.navigator.userAgent : null,
        }),
      });

      localStorage.setItem(FEEDBACK_COOLDOWN_KEY, Date.now().toString());
      setStatus('sent');
    } catch {
      setErrorMessage('일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.');
      setStatus('error');
    }
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
          className="block w-full rounded-md bg-primary px-4 py-2 text-center text-sm font-medium text-primary-fg hover:bg-primary-hover"
        >
          홈으로 돌아가기
        </Link>
      </div>
    );
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4" noValidate>
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
        <label
          htmlFor="feedback-type"
          className="block text-sm font-medium text-fg-secondary"
        >
          유형
        </label>
        <select
          id="feedback-type"
          value={type}
          onChange={(e) => setType(e.target.value as FeedbackType)}
          className="mt-1 block w-full rounded-md border border-border bg-bg-surface px-3 py-2 text-sm shadow-sm focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
        >
          <option value="bug">{FEEDBACK_TYPE_LABELS.bug}</option>
          <option value="question">{FEEDBACK_TYPE_LABELS.question}</option>
          <option value="suggestion">{FEEDBACK_TYPE_LABELS.suggestion}</option>
        </select>
      </div>

      <div>
        <label
          htmlFor="feedback-subject"
          className="block text-sm font-medium text-fg-secondary"
        >
          제목
        </label>
        <input
          id="feedback-subject"
          type="text"
          required
          maxLength={FEEDBACK_LIMITS.SUBJECT_MAX}
          value={subject}
          onChange={(e) => setSubject(e.target.value)}
          className="mt-1 block w-full rounded-md border border-border bg-bg-surface px-3 py-2 text-sm shadow-sm focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
          placeholder="한 줄 요약"
        />
      </div>

      <div>
        <label
          htmlFor="feedback-body"
          className="block text-sm font-medium text-fg-secondary"
        >
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
          className="mt-1 block w-full rounded-md border border-border bg-bg-surface px-3 py-2 text-sm shadow-sm focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
          placeholder={`재현 단계, 기대 동작, 실제 동작 등 구체적으로 작성해 주세요. (${FEEDBACK_LIMITS.BODY_MIN}~${FEEDBACK_LIMITS.BODY_MAX}자)`}
        />
        <p className="mt-1 text-xs text-fg-muted">
          {body.length} / {FEEDBACK_LIMITS.BODY_MAX}
        </p>
      </div>

      <div>
        <label
          htmlFor="feedback-email"
          className="block text-sm font-medium text-fg-secondary"
        >
          이메일 {user?.email ? '(로그인 계정)' : ''}
        </label>
        <input
          id="feedback-email"
          type="email"
          required
          readOnly={!!user?.email}
          value={email}
          onChange={(e) => setEmailInput(e.target.value)}
          className="mt-1 block w-full rounded-md border border-border bg-bg-surface px-3 py-2 text-sm shadow-sm focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary read-only:bg-bg-muted"
          placeholder="you@example.com"
        />
      </div>

      {errorMessage && (
        <p className="text-sm text-danger">{errorMessage}</p>
      )}

      <button
        type="submit"
        disabled={status === 'loading' || cooldownLeft > 0}
        className="w-full cursor-pointer rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-fg hover:bg-primary-hover disabled:cursor-not-allowed disabled:opacity-50"
      >
        {status === 'loading'
          ? '전송 중...'
          : cooldownLeft > 0
            ? `${cooldownLeft}초 후 재시도 가능`
            : '피드백 보내기'}
      </button>

      <p className="text-xs text-fg-muted">
        본 서비스는 투자 자문이 아닙니다. 투자 판단과 그에 따른 책임은 사용자 본인에게 있으며,
        개별 투자 자문 요청에는 응답하지 않습니다.
      </p>
    </form>
  );
}
