import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: '면책 고지 · AI Stock Advisor',
  description: '본 서비스의 정보 제공 성격 및 면책 사항 안내.',
};

export default function DisclaimerPage() {
  return (
    <main className="mx-auto w-full max-w-3xl flex-1 px-4 py-10 sm:px-6">
      <h1 className="text-2xl font-semibold text-zinc-900 dark:text-zinc-50">
        면책 고지 (Disclaimer)
      </h1>

      <section className="mt-6 space-y-4 text-sm leading-relaxed text-zinc-700 dark:text-zinc-300">
        <p>
          본 서비스(&lsquo;AI Stock Advisor&rsquo;, 이하 &ldquo;서비스&rdquo;)에서
          제공되는 모든 정보는 <strong>투자 참고용</strong>이며, 투자 자문이나
          특정 금융상품에 대한 매수·매도 권유가 아닙니다.
        </p>
        <p>
          시세 데이터, 기술적 지표, AI 생성 요약 및 뉴스 링크 등 일체의 콘텐츠는
          공개된 시장 데이터와 공개 뉴스를 가공·요약한 결과로, 오차·지연·누락이
          있을 수 있습니다. 서비스 운영자는 해당 정보의 정확성·완전성·적시성에
          대해 어떠한 보증도 제공하지 않습니다.
        </p>
        <p>
          모든 투자 판단과 그에 따른 책임은 서비스 이용자 본인에게 있으며,
          서비스 이용 결과 발생하는 직·간접적 손해에 대해 서비스 운영자는 법령이
          허용하는 최대 범위 내에서 책임을 지지 않습니다.
        </p>
        <p>
          과거 성과(가격·수익률·지표 추세)는 미래의 수익을 보장하지 않습니다.
          중요한 투자 결정은 본 서비스 외에 복수의 독립된 정보원과 필요 시
          전문가 상담을 병행하시기 바랍니다.
        </p>

        <h2 className="mt-6 text-lg font-semibold text-zinc-900 dark:text-zinc-50">
          AI 분석 결과에 대한 추가 고지
        </h2>
        <p>
          서비스가 표시하는 AI 분석 결과는 공개된 시장 데이터와 뉴스를 기반으로
          한 알고리즘 출력이며, 전문 금융 자문이 아닙니다. AI 모델의 특성상
          사실과 다른 요약이 생성될 가능성이 있어, 중요한 정보는 원문 링크를
          통해 직접 확인하시기 바랍니다.
        </p>

        <p className="text-xs text-zinc-500">
          본 문구는 초안이며, 정식 런칭 전 법률 검토를 거쳐 최종화될 수 있습니다.
        </p>
      </section>
    </main>
  );
}
