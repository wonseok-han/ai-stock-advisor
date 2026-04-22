'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';

import { createClient } from '@/lib/supabase/client';

type Status = 'checking' | 'invalid' | 'ready' | 'loading' | 'error';

/**
 * 비밀번호 재설정 완료 폼.
 * 세션 확인 → 신규 비밀번호 입력/검증 → Supabase updateUser({ password }) → 홈 이동.
 * 세션이 없거나 만료된 링크로 접근 시 'invalid' 상태로 재요청 경로를 안내한다.
 */
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
      setErrorMessage(mapErrorMessage(error.message));
      setStatus('error');
      return;
    }

    router.push('/');
    router.refresh();
  };

  if (status === 'checking') {
    return (
      <div className="rounded-md border border-border bg-bg p-4 text-center">
        <p className="text-sm text-fg-secondary">세션 확인 중...</p>
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
          className="block w-full rounded-md bg-primary px-4 py-2 text-center text-sm font-medium text-primary-fg hover:bg-primary-hover"
        >
          재설정 메일 다시 요청하기
        </Link>
      </div>
    );
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div>
        <label
          htmlFor="reset-password"
          className="block text-sm font-medium text-fg-secondary"
        >
          새 비밀번호
        </label>
        <input
          id="reset-password"
          type="password"
          required
          minLength={6}
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          className="mt-1 block w-full rounded-md border border-border bg-bg-surface px-3 py-2 text-sm shadow-sm focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
          placeholder="6자 이상 입력해주세요."
        />
      </div>

      <div>
        <label
          htmlFor="reset-confirm"
          className="block text-sm font-medium text-fg-secondary"
        >
          비밀번호 확인
        </label>
        <input
          id="reset-confirm"
          type="password"
          required
          minLength={6}
          value={confirmPassword}
          onChange={(e) => setConfirmPassword(e.target.value)}
          className="mt-1 block w-full rounded-md border border-border bg-bg-surface px-3 py-2 text-sm shadow-sm focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
          placeholder="비밀번호 재입력"
        />
      </div>

      {errorMessage && (
        <p className="text-sm text-danger">{errorMessage}</p>
      )}

      <button
        type="submit"
        disabled={status === 'loading'}
        className="w-full cursor-pointer rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-fg hover:bg-primary-hover disabled:opacity-50"
      >
        {status === 'loading' ? '변경 중...' : '비밀번호 변경'}
      </button>
    </form>
  );
}

function mapErrorMessage(message: string): string {
  const lower = message.toLowerCase();
  if (lower.includes('should be at least 6 characters')) {
    return '비밀번호는 6자 이상이어야 합니다.';
  }
  if (lower.includes('should be different from the old password')) {
    return '기존 비밀번호와 다른 비밀번호를 입력해 주세요.';
  }
  if (lower.includes('invalid') || lower.includes('expired') || lower.includes('jwt')) {
    return '링크가 만료되었거나 유효하지 않습니다. 재설정 메일을 다시 요청해 주세요.';
  }
  return message;
}
