'use client';

import { useState } from 'react';

import { useAccuracy } from '@/features/stock-detail/hooks/use-accuracy';
import { AiAccuracyTooltip } from '@/features/stock-detail/components/ai-accuracy-tooltip';

import type { AiAccuracyWindow } from '@/types/ai-accuracy';

/**
 * 전역 AI 시그널 정합도 배지.
 *
 * 참조: docs/02-design/features/signal-accuracy.design.md §5.2, §5.3
 *
 * 표시 규칙:
 *   - 로딩 / 에러 / 샘플 미달(sampleSizeSufficient=false) → 렌더하지 않음 (silent hide)
 *   - 정상: "{window}일 방향 일치율 {pct}% · 샘플 {n}건" 배지 + hover/focus 시 bySignal tooltip
 *
 * 용어 정책 (design §10.2 / CI 금지어): 확정적 지표 용어 금지 → "정합도/방향 일치율".
 */
export function AiAccuracyBadge({
  window = 30,
}: {
  window?: AiAccuracyWindow;
}) {
  const { data, isLoading, error } = useAccuracy(window);
  const [open, setOpen] = useState(false);

  if (isLoading || error || !data) return null;
  if (!data.sampleSizeSufficient || data.hitRate == null) return null;

  const pct = Math.round(data.hitRate * 100);

  return (
    <div className="relative inline-block">
      <button
        type="button"
        aria-label={`최근 ${data.window}일 AI 시그널 방향 일치율 ${pct}퍼센트, 샘플 ${data.sampleSize}건. 상세 보기`}
        aria-expanded={open}
        onMouseEnter={() => setOpen(true)}
        onMouseLeave={() => setOpen(false)}
        onFocus={() => setOpen(true)}
        onBlur={() => setOpen(false)}
        onClick={() => setOpen((v) => !v)}
        className="inline-flex items-center gap-1 rounded-md bg-bg-muted px-2 py-0.5 text-[11px] text-fg-secondary transition hover:bg-bg-surface"
      >
        <span className="font-medium">{data.window}일 방향 일치율</span>
        <span className="font-semibold text-fg">
          {pct}%
        </span>
        <span className="text-fg-muted">·</span>
        <span className="text-fg-muted">
          샘플 {data.sampleSize}건
        </span>
        <span aria-hidden="true" className="text-fg-muted">
          ⓘ
        </span>
      </button>
      {open ? <AiAccuracyTooltip data={data} /> : null}
    </div>
  );
}
