import Link from 'next/link';

export function DisclaimerBanner() {
  return (
    <div className="border-t border-border">
      <div className="mx-auto flex w-full max-w-7xl flex-wrap items-center justify-between gap-2 px-4 py-2 text-[11px] text-fg-muted sm:px-6">
        <p>
          본 서비스는 투자 <strong className="text-fg-secondary">참고용 정보</strong>이며, 투자 자문이 아닙니다.
          모든 투자 판단과 책임은 사용자 본인에게 있습니다.
        </p>
        <Link
          href="/legal/disclaimer"
          className="shrink-0 text-fg-muted underline underline-offset-2 transition-colors hover:text-fg-secondary hover:no-underline"
        >
          전체 고지 보기
        </Link>
      </div>
    </div>
  );
}
