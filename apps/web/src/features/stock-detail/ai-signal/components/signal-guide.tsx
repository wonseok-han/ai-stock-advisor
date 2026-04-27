'use client';

import { CollapsibleSection } from '@/features/stock-detail/ai-signal/components/collapsible-section';

export function SignalGuide() {
  return (
    <CollapsibleSection title="이 분석은 이렇게 읽으세요" defaultOpen={false}>
      <ul className="flex flex-col gap-1.5 text-xs text-fg-secondary">
        <li className="flex items-start gap-2">
          <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-primary/40" aria-hidden="true" />
          <span>시그널은 시장 데이터·기술 지표·뉴스를 종합한 AI의 방향성 요약입니다</span>
        </li>
        <li className="flex items-start gap-2">
          <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-primary/40" aria-hidden="true" />
          <span>투자 추천이 아니며, 하나의 참고 관점으로 활용해 주세요</span>
        </li>
        <li className="flex items-start gap-2">
          <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-primary/40" aria-hidden="true" />
          {/* disclaimer */}
          <span>분석 확신도는 AI가 자체 판단에 느끼는 확신이며, 주가 예측 확률이 아닙니다</span>
        </li>
        <li className="flex items-start gap-2">
          <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-primary/40" aria-hidden="true" />
          <span>방향 일치율은 과거 시그널이 실제 주가 방향과 얼마나 일치했는지 보여줍니다</span>
        </li>
      </ul>
    </CollapsibleSection>
  );
}
