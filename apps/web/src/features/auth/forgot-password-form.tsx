'use client';

import { useState } from 'react';

import { createClient } from '@/lib/supabase/client';

/**
 * 비밀번호 재설정 요청 폼.
 * 이메일 입력 → Supabase resetPasswordForEmail → 안내 표시.
 * 보안 정책상 이메일 존재 여부와 무관하게 성공 응답(sent)을 표시한다.
 */
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
      setErrorMessage(mapErrorMessage(error.message));
      setStatus('error');
      return;
    }

    setStatus('sent');
  };

  if (status === 'sent') {
    return (
      <div className="rounded-xl bg-emerald-500/10 p-4 text-center">
        <p className="text-sm font-medium text-success">
          재설정 메일을 발송했습니다.
        </p>
        <p className="mt-1 text-xs text-fg-secondary">
          이메일의 링크를 클릭해 새 비밀번호를 설정해 주세요. 링크는 1시간 후 만료됩니다.
        </p>
      </div>
    );
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div>
        <label
          htmlFor="forgot-email"
          className="block text-sm font-medium text-fg-secondary"
        >
          이메일
        </label>
        <input
          id="forgot-email"
          type="email"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          className="mt-1 block w-full rounded-xl border border-border bg-bg-surface px-3 py-2.5 text-sm outline-none transition-colors focus:border-primary focus:ring-1 focus:ring-primary"
          placeholder="you@example.com"
        />
      </div>

      {errorMessage && (
        <p className="text-sm text-danger">{errorMessage}</p>
      )}

      <button
        type="submit"
        disabled={status === 'loading'}
        className="w-full cursor-pointer rounded-xl bg-primary px-4 py-2.5 text-sm font-medium text-primary-fg transition-colors hover:bg-primary-hover disabled:opacity-50"
      >
        {status === 'loading' ? '처리 중...' : '재설정 메일 보내기'}
      </button>
    </form>
  );
}

function mapErrorMessage(message: string): string {
  const lower = message.toLowerCase();
  if (lower.includes('rate limit')) {
    return '요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.';
  }
  if (lower.includes('invalid email')) {
    return '올바른 이메일 형식을 입력해 주세요.';
  }
  return message;
}
