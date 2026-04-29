'use client';

import Link from 'next/link';

import { useAuth } from '@/features/auth/auth-provider';

export function AiSignalLinkCard({ ticker }: { ticker: string }) {
  const { user, isLoading } = useAuth();

  if (isLoading) return null;

  if (!user) {
    return (
      <section className="card p-5">
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-primary/10">
            <svg
              aria-hidden="true"
              className="h-5 w-5 text-primary"
              fill="none"
              viewBox="0 0 24 24"
              strokeWidth={1.5}
              stroke="currentColor"
            >
              <path strokeLinecap="round" strokeLinejoin="round" d="M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 00-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 003.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 003.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 00-3.09 3.09zM18.259 8.715L18 9.75l-.259-1.035a3.375 3.375 0 00-2.455-2.456L14.25 6l1.036-.259a3.375 3.375 0 002.455-2.456L18 2.25l.259 1.035a3.375 3.375 0 002.455 2.456L21.75 6l-1.036.259a3.375 3.375 0 00-2.455 2.456zM16.894 20.567L16.5 21.75l-.394-1.183a2.25 2.25 0 00-1.423-1.423L13.5 18.75l1.183-.394a2.25 2.25 0 001.423-1.423l.394-1.183.394 1.183a2.25 2.25 0 001.423 1.423l1.183.394-1.183.394a2.25 2.25 0 00-1.423 1.423z" />
            </svg>
          </div>
          <div>
            <p className="text-sm font-medium text-fg">
              로그인하면 AI 분석과 SEC 공시 해석을 볼 수 있어요
            </p>
            <p className="mt-0.5 text-xs text-fg-muted">
              시장 데이터 · 기술 지표 · 뉴스 종합 시그널 + 미국 공시 AI 요약
            </p>
          </div>
        </div>
        <Link
          href="/auth/login"
          className="mt-4 inline-block rounded-xl bg-primary px-5 py-2 text-sm font-medium text-primary-fg transition-colors hover:bg-primary-hover"
        >
          로그인하기
        </Link>
      </section>
    );
  }

  return (
    <Link
      href={`/stock/${ticker}/ai`}
      className="card group flex items-center justify-between p-5 transition-colors hover:bg-bg-muted"
    >
      <div className="flex items-center gap-3">
        <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-primary/10">
          <svg
            aria-hidden="true"
            className="h-5 w-5 text-primary"
            fill="none"
            viewBox="0 0 24 24"
            strokeWidth={1.5}
            stroke="currentColor"
          >
            <path strokeLinecap="round" strokeLinejoin="round" d="M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 00-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 003.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 003.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 00-3.09 3.09zM18.259 8.715L18 9.75l-.259-1.035a3.375 3.375 0 00-2.455-2.456L14.25 6l1.036-.259a3.375 3.375 0 002.455-2.456L18 2.25l.259 1.035a3.375 3.375 0 002.455 2.456L21.75 6l-1.036.259a3.375 3.375 0 00-2.455 2.456zM16.894 20.567L16.5 21.75l-.394-1.183a2.25 2.25 0 00-1.423-1.423L13.5 18.75l1.183-.394a2.25 2.25 0 001.423-1.423l.394-1.183.394 1.183a2.25 2.25 0 001.423 1.423l1.183.394-1.183.394a2.25 2.25 0 00-1.423 1.423z" />
          </svg>
        </div>
        <div>
          <h2 className="text-sm font-semibold text-fg">이 종목, AI로 분석해볼까요?</h2>
          <p className="text-xs text-fg-muted">
            시장 데이터 · 기술 지표 · 뉴스를 종합한 참고 시그널
          </p>
        </div>
      </div>
      <svg
        aria-hidden="true"
        className="h-5 w-5 text-fg-muted transition-transform group-hover:translate-x-1 group-hover:text-fg"
        fill="none"
        viewBox="0 0 24 24"
        strokeWidth={2}
        stroke="currentColor"
      >
        <path strokeLinecap="round" strokeLinejoin="round" d="M8.25 4.5l7.5 7.5-7.5 7.5" />
      </svg>
    </Link>
  );
}
