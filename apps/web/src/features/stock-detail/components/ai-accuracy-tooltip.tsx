'use client';

import type {
  AiAccuracyBucketStat,
  AiAccuracySignal,
  AiAccuracySummary,
} from '@/types/ai-accuracy';

/**
 * 정합도 배지 tooltip — bySignal 브레이크다운.
 *
 * 참조: docs/02-design/features/signal-accuracy.design.md §5.3
 *
 * 설계 원칙: 확정적 표현(§10.2 금지어) 사용 금지. "방향 일치율"로 표현.
 */
export function AiAccuracyTooltip({ data }: { data: AiAccuracySummary }) {
  const entries = Object.entries(data.bySignal) as [
    AiAccuracySignal,
    AiAccuracyBucketStat,
  ][];
  const evaluatedThrough = data.evaluatedThrough
    ? new Date(data.evaluatedThrough).toLocaleDateString('ko-KR')
    : '—';

  return (
    <div
      role="tooltip"
      className="absolute right-0 top-full z-10 mt-1 w-72 rounded-md border border-border bg-bg-surface p-3 shadow-lg"
    >
      <p className="text-xs font-semibold text-fg-secondary">
        {data.window}일 방향 일치율
      </p>
      <p className="mt-0.5 text-[11px] text-fg-muted">
        샘플 {data.sampleSize}건 · 평가 기준일 {evaluatedThrough}
      </p>

      {entries.length > 0 ? (
        <table className="mt-2 w-full border-collapse text-xs">
          <thead>
            <tr className="border-b border-border text-left text-[10px] text-fg-muted">
              <th className="py-1 font-medium">시그널</th>
              <th className="py-1 font-medium">샘플</th>
              <th className="py-1 text-right font-medium">일치율</th>
            </tr>
          </thead>
          <tbody>
            {entries.map(([signal, stat]) => (
              <tr
                key={signal}
                className="border-b border-border last:border-0"
              >
                <td className="py-1 text-fg-secondary">
                  {signalLabel(signal)}
                </td>
                <td className="py-1 text-fg-muted">
                  {stat.count}
                </td>
                <td className="py-1 text-right text-fg-secondary">
                  {formatRate(stat.hitRate)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : null}

      <p className="mt-2 text-[10px] leading-relaxed text-fg-muted">
        {data.disclaimer}
      </p>
    </div>
  );
}

function signalLabel(signal: AiAccuracySignal): string {
  switch (signal) {
    case 'STRONG_BUY':
      return '강한 긍정';
    case 'BUY':
      return '긍정';
    case 'NEUTRAL':
      return '중립';
    case 'SELL':
      return '부정';
    case 'STRONG_SELL':
      return '강한 부정';
  }
}

function formatRate(rate: number): string {
  return `${Math.round(rate * 100)}%`;
}
