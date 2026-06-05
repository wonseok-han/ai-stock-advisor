package com.nowini.stock.domain;

import java.util.Map;

/**
 * 단일 종목 기술 지표 스냅샷. ta4j 계산 결과 + 한국어 툴팁.
 * 참조: docs/02-design/features/mvp.design.md §3.2, §3.5.
 */
public record IndicatorSnapshot(
        String ticker,
        double rsi14,
        Macd macd,
        Bollinger bollinger,
        MovingAverage movingAverage,
        long avgVolume20d,
        Map<String, String> tooltipsKo
) {
    public record Macd(double macd, double signal, double histogram) {
    }

    public record Bollinger(double upper, double middle, double lower, double percentB) {
    }

    /**
     * 이동평균. ma5/20/60 은 항상 산출(지표 계산 최소 60봉 보장).
     * ma10/50/120/200 은 봉 수 부족(예: 상장 1년 미만) 시 null.
     */
    public record MovingAverage(
            double ma5, Double ma10, double ma20, Double ma50,
            double ma60, Double ma120, Double ma200) {
    }
}
