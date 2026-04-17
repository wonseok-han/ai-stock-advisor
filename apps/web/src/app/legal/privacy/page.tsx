import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: '개인정보 처리방침 · AI Stock Advisor',
  description: 'AI Stock Advisor 개인정보 수집·이용·보관에 대한 안내.',
};

export default function PrivacyPage() {
  return (
    <main className="mx-auto w-full max-w-3xl flex-1 px-4 py-10 sm:px-6">
      <h1 className="text-2xl font-semibold text-zinc-900 dark:text-zinc-50">
        개인정보 처리방침
      </h1>

      <section className="mt-6 space-y-4 text-sm leading-relaxed text-zinc-700 dark:text-zinc-300">
        <p className="text-xs text-zinc-500">
          시행일: 2026년 4월 17일 · 정식 런칭 전 법률 검토를 거쳐 최종화됩니다.
        </p>

        <h2 className="text-lg font-semibold text-zinc-900 dark:text-zinc-50">
          1. 수집하는 개인정보 항목
        </h2>
        <p>
          서비스는 운영에 필요한 최소 정보만 수집합니다.
        </p>
        <ul className="list-disc pl-6">
          <li>
            <strong>회원가입 시:</strong> 이메일 주소 또는 소셜 로그인(Google) OAuth
            식별자
          </li>
          <li>
            <strong>서비스 이용 시:</strong> 북마크 종목 목록, 알림 설정(종목·조건),
            푸시 알림 구독 정보(브라우저 엔드포인트)
          </li>
          <li>
            <strong>자동 수집:</strong> 접속 IP, 브라우저 종류, 요청 경로 등 접속
            기록
          </li>
          <li>
            <strong>쿠키:</strong> 필수 세션 쿠키 (분석 쿠키 미사용)
          </li>
        </ul>

        <h2 className="text-lg font-semibold text-zinc-900 dark:text-zinc-50">
          2. 이용 목적
        </h2>
        <ul className="list-disc pl-6">
          <li>회원 인증 및 계정 관리</li>
          <li>북마크·알림 등 개인화 기능 제공</li>
          <li>푸시 알림(가격 변동, 뉴스, AI 시그널) 발송</li>
          <li>서비스 제공 및 장애 대응</li>
          <li>이상 트래픽·어뷰징 탐지</li>
          <li>서비스 품질 개선을 위한 비식별 통계 분석</li>
        </ul>

        <h2 className="text-lg font-semibold text-zinc-900 dark:text-zinc-50">
          3. 보관 기간 및 파기
        </h2>
        <ul className="list-disc pl-6">
          <li>
            <strong>회원 정보:</strong> 회원 자격이 유지되는 동안 보관합니다.
          </li>
          <li>
            <strong>회원 탈퇴 시:</strong> 북마크, 알림 설정, 푸시 구독 정보는 즉시
            삭제됩니다. 계정 식별 정보(이메일, 탈퇴 일시, 탈퇴 사유)는 관련 법령 및
            내부 정책에 따라 <strong>탈퇴일로부터 2년간 보관 후 파기</strong>합니다.
          </li>
          <li>
            <strong>접속 기록:</strong> 관련 법령이 정한 기간 또는 운영상 필요한
            최소 기간 동안 보관 후 파기합니다.
          </li>
        </ul>

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
        <ul className="list-disc pl-6">
          <li>
            <strong>인증:</strong> 회원 인증에 Supabase Auth를 이용하며, 해당
            서비스 제공자의 개인정보 처리방침이 함께 적용됩니다.
          </li>
          <li>
            <strong>시세·뉴스 데이터:</strong> 외부 제공자(Finnhub, Yahoo Finance
            등)로부터 시장 데이터를 수신하여 표시합니다. 해당 제공자가 최종
            이용자의 개인정보를 직접 수집하지는 않습니다.
          </li>
          <li>
            <strong>AI 분석:</strong> Google Gemini API를 이용하며, 이용자의
            개인정보는 AI 분석 요청에 포함되지 않습니다.
          </li>
        </ul>

        <h2 className="text-lg font-semibold text-zinc-900 dark:text-zinc-50">
          6. 회원 탈퇴 및 계정 복구
        </h2>
        <ul className="list-disc pl-6">
          <li>
            이용자는 마이페이지에서 언제든 회원 탈퇴를 요청할 수 있습니다.
          </li>
          <li>
            탈퇴 시 서비스 내 이용 데이터(북마크, 알림 설정, 푸시 구독)는 즉시
            삭제되며, 계정은 로그인 차단 처리됩니다.
          </li>
          <li>
            탈퇴 후 동일 이메일로 재가입을 시도하면 계정 복구가 가능합니다.
            복구 시 이전에 삭제된 이용 데이터(북마크, 알림 등)는 복원되지
            않습니다.
          </li>
        </ul>

        <h2 className="text-lg font-semibold text-zinc-900 dark:text-zinc-50">
          7. 권리 행사 방법
        </h2>
        <p>
          이용자는 언제든 자신의 개인정보 열람·정정·삭제·처리정지를 요청할 수
          있습니다. 마이페이지에서 직접 관리하거나, 서비스 내 문의 수단을 통해
          요청할 수 있습니다.
        </p>
      </section>
    </main>
  );
}
