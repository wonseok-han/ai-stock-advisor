import Link from 'next/link';

import { SignupForm } from '@/features/auth/signup-form';
import { SocialLogin } from '@/features/auth/social-login';

import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: '회원가입 — AI Stock Advisor',
};

export default function SignupPage() {
  return (
    <main className="flex flex-1 items-center justify-center px-4 py-12">
      <div className="w-full max-w-sm space-y-6">
        <div className="text-center">
          <h1 className="text-xl font-semibold text-zinc-900 dark:text-zinc-100">
            회원가입
          </h1>
          <p className="mt-1 text-sm text-zinc-500">
            계정을 만들어 북마크와 알림 기능을 이용하세요.
          </p>
        </div>

        <SignupForm />

        <div className="relative">
          <div className="absolute inset-0 flex items-center">
            <div className="w-full border-t border-zinc-200 dark:border-zinc-700" />
          </div>
          <div className="relative flex justify-center text-xs">
            <span className="bg-zinc-50 px-2 text-zinc-500 dark:bg-black dark:text-zinc-400">
              또는
            </span>
          </div>
        </div>

        <SocialLogin />

        <p className="text-center text-sm text-zinc-500">
          이미 계정이 있으신가요?{' '}
          <Link
            href="/auth/login"
            className="text-blue-600 hover:underline dark:text-blue-400"
          >
            로그인
          </Link>
        </p>
      </div>
    </main>
  );
}
