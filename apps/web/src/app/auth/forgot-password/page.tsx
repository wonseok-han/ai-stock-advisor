import Link from 'next/link';

import { ForgotPasswordForm } from '@/features/auth/forgot-password-form';

import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: '비밀번호 재설정 — 지금이니?!',
};

export default function ForgotPasswordPage() {
  return (
    <main className="flex flex-1 items-center justify-center px-4 py-12">
      <div className="w-full max-w-sm space-y-6">
        <div className="text-center">
          <h1 className="text-xl font-semibold text-fg">
            비밀번호 재설정
          </h1>
          <p className="mt-1 text-sm text-fg-muted">
            가입하신 이메일을 입력하시면 재설정 링크를 보내드립니다.
          </p>
        </div>

        <ForgotPasswordForm />

        <p className="text-center text-sm text-fg-muted">
          <Link
            href="/auth/login"
            className="text-primary hover:underline"
          >
            로그인으로 돌아가기
          </Link>
        </p>
      </div>
    </main>
  );
}
