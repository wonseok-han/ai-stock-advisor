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
