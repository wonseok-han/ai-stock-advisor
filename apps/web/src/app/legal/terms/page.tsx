import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: '이용약관 · AI Stock Advisor',
  description: 'AI Stock Advisor 서비스 이용약관.',
};

export default function TermsPage() {
  return (
    <main className="mx-auto w-full max-w-3xl flex-1 px-4 py-10 sm:px-6">
      <h1 className="text-2xl font-semibold text-zinc-900 dark:text-zinc-50">
        이용약관 (Terms of Service)
      </h1>

      <section className="mt-6 space-y-4 text-sm leading-relaxed text-zinc-700 dark:text-zinc-300">
        <p className="text-xs text-zinc-500">
          시행일: 2026년 4월 17일 · 정식 런칭 전 법률 검토를 거쳐 최종화됩니다.
        </p>

        <h2 className="text-lg font-semibold text-zinc-900 dark:text-zinc-50">
          제1조 (서비스의 목적)
        </h2>
        <p>
          본 서비스(&ldquo;AI Stock Advisor&rdquo;)는 미국 상장 주식에 대한
          공개된 시장 데이터, 기술 지표, 뉴스 요약 등의{' '}
          <strong>투자 참고용 정보</strong>를 제공하는 것을 목적으로 합니다.
          본 서비스는 특정 금융상품의 매수·매도를 권유하지 않으며, 자본시장법에
          따른 투자자문업·투자일임업을 영위하지 않습니다.
        </p>

        <h2 className="text-lg font-semibold text-zinc-900 dark:text-zinc-50">
          제2조 (면책)
        </h2>
        <ul className="list-disc pl-6">
          <li>
            서비스 이용 결과 발생한 투자 손익의 책임은 이용자 본인에게 있습니다.
          </li>
          <li>
            서비스 운영자는 제공 정보의 정확성·완전성·적시성에 대해 보증하지
            않으며, 이용자에게 발생한 직·간접 손해에 대해 법령이 허용하는 최대
            범위에서 책임을 지지 않습니다.
          </li>
          <li>
            본 서비스의 AI 분석 결과는 공개된 시장 데이터와 뉴스를 기반으로 한
            알고리즘 출력이며, 전문 금융 자문이 아닙니다. AI 분석에 기반한 투자
            판단의 책임은 전적으로 이용자에게 있습니다.
          </li>
          <li>
            과거 데이터에 기반한 기술 지표·시그널은 미래 수익을 보장하지 않습니다.
          </li>
        </ul>

        <h2 className="text-lg font-semibold text-zinc-900 dark:text-zinc-50">
          제3조 (금지 행위)
        </h2>
        <ul className="list-disc pl-6">
          <li>자동화된 수단(크롤러·봇)을 통한 대량 요청·스크래핑</li>
          <li>제공되는 원시 데이터의 재배포·재판매</li>
          <li>서비스 정상 운영을 방해하는 일체의 행위</li>
          <li>타인의 계정·정보에 대한 무단 접근</li>
        </ul>

        <h2 className="text-lg font-semibold text-zinc-900 dark:text-zinc-50">
          제4조 (계정)
        </h2>
        <ul className="list-disc pl-6">
          <li>
            이용자는 이메일 또는 소셜 로그인(Google)을 통해 계정을 생성할 수
            있습니다.
          </li>
          <li>
            계정 정보의 관리 책임은 이용자에게 있으며, 타인에게 계정을 양도하거나
            공유할 수 없습니다.
          </li>
          <li>
            이용자는 마이페이지에서 언제든 회원 탈퇴를 요청할 수 있습니다. 탈퇴 시
            서비스 내 이용 데이터(북마크, 알림 설정 등)는 즉시 삭제됩니다.
          </li>
          <li>
            탈퇴 후 동일 이메일로 재가입하면 계정 복구가 가능하나, 이전 이용
            데이터는 복원되지 않습니다.
          </li>
          <li>
            계정 식별 정보는 개인정보 처리방침에 따라 탈퇴일로부터 2년간 보관 후
            파기됩니다.
          </li>
        </ul>

        <h2 className="text-lg font-semibold text-zinc-900 dark:text-zinc-50">
          제5조 (알림 서비스)
        </h2>
        <ul className="list-disc pl-6">
          <li>
            이용자는 종목별 가격 변동, 뉴스, AI 시그널에 대한 푸시 알림을 설정할
            수 있습니다.
          </li>
          <li>
            알림은 시장 데이터 및 외부 API에 의존하므로, 실시간성·정확성을
            보장하지 않습니다.
          </li>
          <li>
            알림 내용은 투자 권유가 아닌 정보 제공 목적이며, 이에 기반한 투자
            판단의 책임은 이용자에게 있습니다.
          </li>
        </ul>

        <h2 className="text-lg font-semibold text-zinc-900 dark:text-zinc-50">
          제6조 (지적재산권)
        </h2>
        <ul className="list-disc pl-6">
          <li>
            서비스에서 제공하는 AI 분석 결과, 차트, 기술 지표, UI 디자인 등
            일체의 콘텐츠에 대한 저작권 및 지적재산권은 서비스 운영자에게
            귀속됩니다.
          </li>
          <li>
            이용자는 서비스 콘텐츠를 개인적·비상업적 용도로만 이용할 수 있으며,
            운영자의 사전 서면 동의 없이 복제·배포·전송·2차 저작물 작성 등에
            이용할 수 없습니다.
          </li>
          <li>
            시세·뉴스 등 외부 제공자로부터 수신한 데이터의 저작권은 해당
            제공자에게 있습니다.
          </li>
        </ul>

        <h2 className="text-lg font-semibold text-zinc-900 dark:text-zinc-50">
          제7조 (서비스 변경·중단)
        </h2>
        <p>
          운영자는 서비스 내용·구성·제공 조건을 예고 없이 변경하거나 운영상·
          기술상 필요에 따라 일시 또는 영구 중단할 수 있으며, 이에 따른 손해에
          대해 책임을 지지 않습니다.
        </p>

        <h2 className="text-lg font-semibold text-zinc-900 dark:text-zinc-50">
          제8조 (약관의 변경)
        </h2>
        <ul className="list-disc pl-6">
          <li>
            운영자는 관련 법령에 위배되지 않는 범위에서 본 약관을 변경할 수
            있습니다.
          </li>
          <li>
            약관이 변경되는 경우 시행일 7일 전부터 서비스 내 공지를 통해
            고지합니다. 이용자에게 불리한 변경의 경우 30일 전부터 고지합니다.
          </li>
          <li>
            변경된 약관 시행일 이후에도 서비스를 계속 이용하는 경우 변경 약관에
            동의한 것으로 간주합니다.
          </li>
        </ul>

        <h2 className="text-lg font-semibold text-zinc-900 dark:text-zinc-50">
          제9조 (준거법)
        </h2>
        <p>본 약관의 해석과 분쟁 해결은 대한민국 법령을 준거법으로 합니다.</p>
      </section>
    </main>
  );
}
