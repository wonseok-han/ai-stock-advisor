import type { TimingVerdictType } from "@/types/ai-signal";

/**
 * 단기 진입 / 장기 진입 두 판정을 결합한 "종합 해석" 한 줄.
 *
 * 단기(눌림목·반등)와 장기(추세선 지지·저평가)는 서로 다른 자리를 보므로,
 * "장기는 지금, 단기는 아직" 같은 엇갈림이 자연스럽다. 이 매트릭스가 둘을 합쳐 결론을 준다.
 * 모든 문구는 정보성 서술(권유·명령 아님) — 면책 원칙 유지.
 */
const READ: Record<TimingVerdictType, Record<TimingVerdictType, string>> = {
  // [단기][장기]
  NOW: {
    NOW: "단기·장기 모두 진입 조건이 우호적인 구간입니다.",
    UNCERTAIN:
      "단기 반등 조건은 갖춰졌고 장기는 일부만 충족 — 단기 관점이 우세한 구간입니다.",
    NOT_YET:
      "단기 반등 자리이나 장기 추세·밸류는 아직 — 단기 관점으로 짧게 보는 구간입니다.",
  },
  UNCERTAIN: {
    NOW: "장기 진입 조건이 우세하고 단기는 애매 — 분할 접근·눌림목 대기가 무난한 구간입니다.",
    UNCERTAIN:
      "단기·장기 모두 진입 조건이 일부만 충족돼 관망이 무난한 구간입니다.",
    NOT_YET: "단기는 애매하고 장기 조건은 미흡 — 관망 우위 구간입니다.",
  },
  NOT_YET: {
    NOW: "장기 진입 조건은 우세하나 단기는 미흡 — 장기 관점에서 분할 접근을 고려할 구간입니다.",
    UNCERTAIN: "단기 진입 조건이 미흡하고 장기도 일부만 충족 — 관망 우위 구간입니다.",
    NOT_YET: "단기·장기 모두 진입 조건이 미흡해 관망 구간입니다.",
  },
};

export function overallRead(
  shortVerdict: TimingVerdictType,
  longVerdict: TimingVerdictType,
): string {
  return READ[shortVerdict]?.[longVerdict] ?? "";
}
