import Link from 'next/link';

import { LoginForm } from '@/features/auth/login-form';
import { SocialLogin } from '@/features/auth/social-login';

import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: '로그인 — 지금이니?!',
};

export default function LoginPage() {
  return (
    <main className="flex flex-1 items-center justify-center px-4 py-12">
      <div className="w-full max-w-sm space-y-6">
        <div className="text-center">
          <h1 className="text-xl font-semibold text-fg">
            로그인
          </h1>
          <p className="mt-1 text-sm text-fg-muted">
            북마크와 알림 기능을 사용하려면 로그인하세요.
          </p>
        </div>

        <LoginForm />

        <p className="text-center text-sm">
          <Link
            href="/auth/forgot-password"
            className="text-fg-muted hover:text-fg-secondary hover:underline"
          >
            비밀번호를 잊으셨나요?
          </Link>
        </p>

        <div className="relative">
          <div className="absolute inset-0 flex items-center">
            <div className="w-full border-t border-border" />
          </div>
          <div className="relative flex justify-center text-xs">
            <span className="bg-bg px-2 text-fg-muted">
              또는
            </span>
          </div>
        </div>

        <SocialLogin />

        <p className="text-center text-sm text-fg-muted">
          계정이 없으신가요?{' '}
          <Link
            href="/auth/signup"
            className="text-primary hover:underline"
          >
            회원가입
          </Link>
        </p>
      </div>
    </main>
  );
}
