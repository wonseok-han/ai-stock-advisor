import { FeedbackForm } from '@/features/feedback/feedback-form';

import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: '피드백 보내기 — 지금이니?!',
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
