package com.aistockadvisor.stock.domain;

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
        Map<String, String> tooltipsKo
) {
    public record Macd(double macd, double signal, double histogram) {
    }

    public record Bollinger(double upper, double middle, double lower, double percentB) {
    }

    public record MovingAverage(double ma5, double ma20, double ma60) {
    }
}
