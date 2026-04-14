import Link from 'next/link';

/**
 * 상단 면책 배너 (모든 페이지 노출).
 * docs/planning/07-legal-compliance.md §7.2 근거.
 */
export function DisclaimerBanner() {
  return (
    <div className="border-b border-amber-200 bg-amber-50 text-amber-900 dark:border-amber-900/40 dark:bg-amber-950/40 dark:text-amber-100">
      <div className="mx-auto flex w-full max-w-5xl flex-wrap items-center justify-between gap-2 px-4 py-2 text-xs sm:px-6">
        <p>
          본 서비스는 투자 <strong>참고용 정보</strong>이며, 투자 자문이 아닙니다.
          모든 투자 판단과 책임은 사용자 본인에게 있습니다.
        </p>
        <Link
          href="/legal/disclaimer"
          className="shrink-0 underline underline-offset-2 hover:no-underline"
        >
          전체 고지 보기
        </Link>
      </div>
    </div>
  );
}
