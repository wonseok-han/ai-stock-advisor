package com.aistockadvisor.legal;

/**
 * 면책 문구 상수. docs/planning/07-legal-compliance.md 기반.
 * 모든 외부 응답(/news, /ai-signal, /detail)에 반드시 부착.
 */
public final class Disclaimers {

    public static final String VERSION = "v1.0";

    public static final String DEFAULT =
            "본 서비스는 투자 자문이 아닌 분석 도구입니다. 모든 투자 판단과 책임은 사용자 본인에게 있습니다.";

    public static final String NEWS =
            "본 뉴스 번역/요약은 참고용이며 투자 자문이 아닙니다. 원문을 반드시 확인하시고, 투자 판단과 책임은 사용자 본인에게 있습니다.";

    public static final String AI_SIGNAL =
            "본 AI 분석은 참고용 정보이며 투자 자문이나 매매 추천이 아닙니다. 모든 투자 판단과 책임은 사용자 본인에게 있습니다.";

    public static final String MARKET =
            "본 데이터는 참고용이며, 실시간 시세와 차이가 있을 수 있습니다. 모든 투자 판단과 책임은 사용자 본인에게 있습니다.";

    public static final String MARKET_NEWS =
            "본 뉴스 번역/요약은 AI에 의한 참고용이며, 원문과 차이가 있을 수 있습니다. 투자 판단과 책임은 사용자 본인에게 있습니다.";

    public static final String MARKET_MOVERS =
            "변동률은 인기 종목 기준이며, 전체 시장 급등/급락과 다를 수 있습니다. 모든 투자 판단과 책임은 사용자 본인에게 있습니다.";

    private Disclaimers() {
    }
}
