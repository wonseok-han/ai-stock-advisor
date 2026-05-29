package com.nowini.market.domain;

import java.util.List;

/**
 * 시장 국면 종합 응답 (공개 지표). AI 해석은 별도 인증 엔드포인트(/regime/ai)에서 제공.
 * <p>
 * 참조: docs/02-design/features/market-regime.design.md §1.2
 */
public record MarketRegimeResponse(
        String asOf,
        Composite composite,
        Axes axes,
        String disclaimer
) {
    /** 종합 국면 점수 (0=공포/저평가 ~ 100=과열/고평가). */
    public record Composite(int score, String label, String labelKo) {}

    public record Axes(Axis valuation, Axis riskSentiment, Axis macro, Axis trendBreadth) {}

    public record Axis(List<Indicator> indicators) {}

    public record Indicator(
            String key,
            String name,
            Double value,
            String unit,
            String zone,
            String note,
            Double prev1w,
            Double prev1m,
            Double prev1y
    ) {
        public static Indicator of(String key, String name, Double value, String unit, String zone, String note) {
            return new Indicator(key, name, value, unit, zone, note, null, null, null);
        }
    }
}
