import Link from 'next/link';

/**
 * 하단 면책 푸터 (모든 페이지 노출).
 * docs/planning/07-legal-compliance.md §7.2 근거.
 */
export function DisclaimerFooter() {
  return (
    <footer className="mt-auto border-t border-border bg-bg-surface text-fg-secondary">
      <div className="mx-auto w-full max-w-5xl px-4 py-6 text-xs leading-relaxed sm:px-6">
        <p className="mb-3">
          본 서비스에서 제공되는 모든 정보는 <strong>투자 참고용</strong>이며,
          투자 자문이나 매수/매도 권유가 아닙니다. 모든 투자 판단과 그에 따른
          책임은 사용자 본인에게 있습니다. 과거 성과는 미래 수익을 보장하지
          않습니다.
        </p>
        <nav className="flex flex-wrap gap-4" aria-label="법적 고지 및 피드백">
          <Link href="/legal/disclaimer" className="hover:underline">
            면책 고지
          </Link>
          <Link href="/legal/terms" className="hover:underline">
            이용약관
          </Link>
          <Link href="/legal/privacy" className="hover:underline">
            개인정보 처리방침
          </Link>
          <Link href="/feedback" className="hover:underline">
            피드백 보내기
          </Link>
        </nav>
        <p className="mt-4 text-[11px] text-fg-muted">
          © {new Date().getFullYear()} 지금이니?! 시세: Yahoo Finance ·
          Finnhub · Twelve Data · FMP · AI: Google Gemini · 차트: TradingView
          Lightweight Charts.
        </p>
      </div>
    </footer>
  );
}
