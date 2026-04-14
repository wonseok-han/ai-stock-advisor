import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: '개인정보 처리방침 · AI Stock Advisor',
  description: 'AI Stock Advisor 개인정보 수집·이용·보관에 대한 안내 초안.',
};

export default function PrivacyPage() {
  return (
    <main className="mx-auto w-full max-w-3xl flex-1 px-4 py-10 sm:px-6">
      <h1 className="text-2xl font-semibold text-zinc-900 dark:text-zinc-50">
        개인정보 처리방침
      </h1>

      <section className="mt-6 space-y-4 text-sm leading-relaxed text-zinc-700 dark:text-zinc-300">
        <p className="text-xs text-zinc-500">
          본 방침은 MVP 기준 초안입니다. 회원 기능 도입 및 정식 런칭 전 법률
          검토를 거쳐 최종화됩니다.
        </p>

        <h2 className="text-lg font-semibold text-zinc-900 dark:text-zinc-50">
          1. 수집하는 개인정보 항목
        </h2>
        <p>
          MVP 단계에서는 회원가입·로그인 없이 이용 가능하며, 서비스 운영상
          필수적인 최소 정보만 수집합니다.
        </p>
        <ul className="list-disc pl-6">
          <li>자동 수집: 접속 IP, 브라우저 종류, 요청 경로 등 접속 기록</li>
          <li>쿠키: 필수 세션 쿠키 (분석 쿠키 미사용)</li>
          <li>
            회원 기능 도입 시 (Phase 4 예정): 이메일 또는 OAuth 식별자, 북마크
            티커 목록
          </li>
        </ul>

        <h2 className="text-lg font-semibold text-zinc-900 dark:text-zinc-50">
          2. 이용 목적
        </h2>
        <ul className="list-disc pl-6">
          <li>서비스 제공 및 장애 대응</li>
          <li>이상 트래픽·어뷰징 탐지</li>
          <li>서비스 품질 개선을 위한 비식별 통계 분석</li>
        </ul>

        <h2 className="text-lg font-semibold text-zinc-900 dark:text-zinc-50">
          3. 보관 기간
        </h2>
        <p>
          접속 기록은 관련 법령이 정한 기간 또는 운영상 필요한 최소 기간 동안
          보관 후 지체 없이 파기합니다. 회원 정보는 회원 탈퇴 시 즉시
          파기합니다.
        </p>

        <h2 className="text-lg font-semibold text-zinc-900 dark:text-zinc-50">
          4. 제3자 제공
        </h2>
        <p>
          운영자는 이용자의 개인정보를 제3자에게 제공하지 않습니다. 단, 법령에
          따른 요청이나 수사기관의 적법한 요구가 있을 경우 관련 법령 절차에
          따라 협조할 수 있습니다.
        </p>

        <h2 className="text-lg font-semibold text-zinc-900 dark:text-zinc-50">
          5. 외부 서비스 이용
        </h2>
        <p>
          서비스는 시세·뉴스 데이터를 외부 제공자(Finnhub 등)로부터 받아
          표시합니다. 해당 제공자가 최종 이용자의 개인정보를 직접 수집하지는
          않습니다. 회원 기능 도입 시에는 인증 서비스(Supabase Auth)를 이용할
          수 있으며, 해당 서비스 제공자의 약관과 개인정보 처리방침이 함께
          적용됩니다.
        </p>

        <h2 className="text-lg font-semibold text-zinc-900 dark:text-zinc-50">
          6. 권리 행사 방법
        </h2>
        <p>
          이용자는 언제든 자신의 개인정보 열람·정정·삭제·처리정지를 요청할 수
          있으며, 요청 접수 창구는 정식 런칭 시 별도 고지됩니다.
        </p>
      </section>
    </main>
  );
}
