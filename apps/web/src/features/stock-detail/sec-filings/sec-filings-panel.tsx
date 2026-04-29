'use client';

import { InfoTooltip } from '@/components/ui/info-tooltip';
import { PanelLoading } from '@/components/ui/panel-loading';
import { useAuth } from '@/features/auth/auth-provider';
import { useSecFilings } from '@/features/stock-detail/sec-filings/hooks/use-sec-filings';
import { cn } from '@/lib/cn';

import type { SecFiling } from '@/types/stock';

export function SecFilingsPanel({ ticker }: { ticker: string }) {
  const { user, isLoading: authLoading } = useAuth();

  if (authLoading || !user) return null;

  return <SecFilingsContent ticker={ticker} />;
}

function SecFilingsContent({ ticker }: { ticker: string }) {
  const { data, isLoading, error } = useSecFilings(ticker);

  if (isLoading) {
    return <PanelLoading title="SEC 공시" text="공시 정보를 불러오고 있어요" />;
  }

  if (error || !data || data.length === 0) return null;

  return (
    <section aria-label="SEC 공시" className="card p-5">
      <div className="flex items-center justify-between">
        <h2 className="text-sm font-semibold text-fg">SEC 공시</h2>
        <InfoTooltip text="미국 SEC에 제출된 최근 공시입니다. 실적·계약·임원 변경·신주 발행 등 중요 이벤트를 포함합니다. 참고용 정보이며 투자 판단의 근거로 사용하지 마세요." />
      </div>

      <ul className="mt-4 flex flex-col divide-y divide-border">
        {data.map((filing, i) => (
          <FilingRow key={i} filing={filing} />
        ))}
      </ul>

      <div className="mt-3 flex items-center justify-between">
        <p className="text-[11px] text-fg-muted">출처: SEC EDGAR 공개 데이터 · 참고용</p>
        <a
          href={`https://www.sec.gov/cgi-bin/browse-edgar?action=getcompany&CIK=${ticker}&type=&dateb=&owner=include&count=10`}
          target="_blank"
          rel="noreferrer noopener"
          className="text-[11px] text-primary hover:underline"
        >
          EDGAR에서 더 보기 →
        </a>
      </div>
    </section>
  );
}

function FilingRow({ filing }: { filing: SecFiling }) {
  const dateStr = new Date(filing.filedAt + 'T00:00:00').toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  });

  const description = filing.summaryKo ?? CATEGORY_DESCRIPTIONS[filing.eventCategory] ?? null;

  return (
    <li className="flex items-start gap-3 py-3 first:pt-0 last:pb-0">
      <span
        className={cn(
          'mt-0.5 shrink-0 rounded px-1.5 py-0.5 text-[10px] font-semibold',
          categoryStyle(filing.eventCategory),
        )}
      >
        {formLabel(filing.form)}
      </span>
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <p className="text-sm font-medium text-fg">{filing.eventCategory}</p>
          {filing.sentiment && (
            <span
              className={cn(
                'shrink-0 rounded px-1.5 py-0.5 text-[10px] font-semibold',
                sentimentStyle(filing.sentiment),
              )}
            >
              {filing.sentiment}
            </span>
          )}
          {filing.documentUrl && (
            <a
              href={filing.documentUrl}
              target="_blank"
              rel="noreferrer noopener"
              className="shrink-0 text-[10px] text-primary hover:underline"
            >
              원문
            </a>
          )}
        </div>
        {description && (
          <p className="mt-0.5 text-xs text-fg-secondary">{description}</p>
        )}
        <p className="mt-0.5 text-[11px] text-fg-muted">
          <time dateTime={filing.filedAt}>{dateStr}</time>
          {filing.daysAgo <= 365 && (
            <span className="ml-1">({filing.daysAgo}일 전)</span>
          )}
        </p>
      </div>
    </li>
  );
}

function formLabel(form: string): string {
  if (form.startsWith('8-K')) return '8-K';
  if (form.startsWith('S-3')) return 'S-3';
  if (form.startsWith('424B')) return '424B';
  return form;
}

const CATEGORY_DESCRIPTIONS: Record<string, string> = {
  '실적 발표': '분기 또는 연간 실적이 공시되었습니다. 매출·순이익·EPS 변동을 확인하세요.',
  '중요 계약 체결': '회사 사업에 중요한 계약이 체결되었습니다.',
  '계약 종료': '기존 중요 계약이 종료 또는 해지되었습니다.',
  '파산·청산': '파산 또는 회사 청산 절차가 개시되었습니다. 주가에 큰 영향을 줄 수 있습니다.',
  '중요 자산 취득·처분': '회사의 주요 자산을 인수하거나 매각했습니다.',
  '부채·금융약정': '부채 또는 금융 약정에 변동이 발생했습니다.',
  '구조조정·비용': '구조조정이나 대규모 비용 인식이 발표되었습니다.',
  '자산가치 손상': '보유 자산의 가치가 하락하여 손상 처리되었습니다.',
  '상장 폐지 위험': '거래소 상장 유지 기준에 미달하여 폐지 위험이 있습니다.',
  '신주 발행': '신주가 발행되었습니다. 기존 주주의 지분이 희석될 수 있습니다.',
  '감사인 변경': '회계 감사법인이 변경되었습니다.',
  '경영권 변경': '회사의 경영권에 변동이 발생했습니다.',
  '임원 교체': '이사회 또는 경영진의 인사 변동이 있었습니다.',
  '정관 변경': '회사 정관(사업 규칙)이 변경되었습니다.',
  '주주총회 결의': '주주총회에서 주요 안건이 의결되었습니다.',
  'FD 규정 공시': '미공개 정보가 공정 공시 규정에 따라 공개되었습니다.',
  '기타 이벤트': '기타 중요 사항이 공시되었습니다.',
  '재무제표 첨부': '재무제표 또는 관련 보충 자료가 제출되었습니다.',
  '기타 공시': '기타 공시 사항이 접수되었습니다.',
  '신주 발행 등록': 'SEC에 신주 발행 등록신청서가 제출되었습니다. 유상증자 전 단계입니다.',
  '공모 조건 확정': '공모 가격과 발행 수량이 확정되었습니다. 지분 희석에 유의하세요.',
  '공모 설명서': '투자 설명서가 업데이트되었습니다.',
};

const NEGATIVE_CATEGORIES = new Set([
  '파산·청산', '상장 폐지 위험', '자산가치 손상', '구조조정·비용', '계약 종료',
]);
const POSITIVE_CATEGORIES = new Set([
  '실적 발표', '중요 계약 체결', '중요 자산 취득·처분', '주주총회 결의',
]);
const DILUTION_CATEGORIES = new Set([
  '신주 발행', '신주 발행 등록', '공모 조건 확정', '공모 설명서',
]);

function sentimentStyle(sentiment: string): string {
  switch (sentiment) {
    case '긍정':
      return 'bg-emerald-500/10 text-success';
    case '부정':
      return 'bg-red-500/10 text-danger';
    default:
      return 'bg-zinc-500/10 text-fg-muted';
  }
}

function categoryStyle(category: string): string {
  if (NEGATIVE_CATEGORIES.has(category)) {
    return 'bg-red-500/10 text-danger';
  }
  if (POSITIVE_CATEGORIES.has(category)) {
    return 'bg-emerald-500/10 text-success';
  }
  if (DILUTION_CATEGORIES.has(category)) {
    return 'bg-amber-500/10 text-amber-400';
  }
  return 'bg-blue-500/10 text-blue-400';
}
