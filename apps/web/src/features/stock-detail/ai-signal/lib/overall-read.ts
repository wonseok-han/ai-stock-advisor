import type { AiSignalClass, TimingVerdictType } from "@/types/ai-signal";

/**
 * 방향 전망(signal)과 진입 조건(timing.verdict)을 결합한 "종합 해석" 한 줄.
 *
 * 두 카드가 서로 다른 질문(어디로 갈까 / 지금 들어갈 자리인가)에 답하기 때문에
 * 나란히 보면 충돌처럼 느껴질 수 있다. 이 매트릭스가 둘을 합쳐 결론을 명확히 한다.
 * timing 은 "저점·눌림목 진입" 기준이라, 강한 상승 전망이어도 과열 구간이면 진입은 신중.
 *
 * 모든 문구는 정보성 서술(권유·명령 아님) — 면책 원칙 유지.
 */
type Direction = "up" | "flat" | "down";

function directionOf(signal: AiSignalClass): Direction | null {
  switch (signal) {
    case "STRONG_BUY":
    case "BUY":
      return "up";
    case "NEUTRAL":
      return "flat";
    case "SELL":
    case "STRONG_SELL":
      return "down";
    default:
      // signal 이 null·누락 등 예상 밖 값일 때(외부 데이터 방어) — 종합 해석 생략
      return null;
  }
}

const READ: Record<Direction, Record<TimingVerdictType, string>> = {
  up: {
    NOW: "상승 전망과 저점 진입 조건이 함께 우호적인 구간입니다.",
    UNCERTAIN:
      "추세 전망은 우호적이나 단기 과열로 저점 진입 자리는 아닙니다 — 눌림목 대기·분할 접근을 고려할 구간입니다.",
    NOT_YET:
      "상승 전망이지만 현재가 부담이 커 저점 진입 자리는 아닙니다 — 추격보다 조정·눌림목을 기다릴 구간입니다.",
  },
  flat: {
    NOW: "방향성은 뚜렷하지 않으나 낙폭과대 등 단기 진입 조건은 일부 갖춰진 구간입니다.",
    UNCERTAIN: "방향성도 진입 조건도 뚜렷하지 않아 관망이 무난한 구간입니다.",
    NOT_YET: "방향성이 약하고 진입 조건도 미흡해 관망 구간입니다.",
  },
  down: {
    NOW: "하락 전망이나 단기 낙폭과대로 기술적 반등 조건은 갖춰진 구간입니다 — 추세 반전 확인이 필요합니다.",
    UNCERTAIN: "하락 전망이며 진입 조건도 엇갈려 신중할 구간입니다.",
    NOT_YET: "하락 전망이고 진입 조건도 미흡해 관망이 무난한 구간입니다.",
  },
};

export function overallRead(
  signal: AiSignalClass,
  verdict: TimingVerdictType,
): string {
  const dir = directionOf(signal);
  if (!dir) return "";
  return READ[dir]?.[verdict] ?? "";
}
