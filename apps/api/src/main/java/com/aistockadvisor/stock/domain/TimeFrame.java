package com.aistockadvisor.stock.domain;

import java.time.Duration;

/**
 * 차트 타임프레임. Finnhub /stock/candle 의 resolution + lookback 기간 매핑.
 * 참조: docs/02-design/features/mvp.design.md §3.2, §3.4.
 *
 * <p>resolution 값은 Finnhub API 규격: "1","5","15","30","60","D","W","M".
 */
public enum TimeFrame {

    D1("5",  Duration.ofDays(1)),
    W1("30", Duration.ofDays(7)),
    M1("D",  Duration.ofDays(30)),
    M3("D",  Duration.ofDays(90)),
    Y1("D",  Duration.ofDays(365)),
    Y5("W",  Duration.ofDays(365L * 5));

    private final String finnhubResolution;
    private final Duration lookback;

    TimeFrame(String finnhubResolution, Duration lookback) {
        this.finnhubResolution = finnhubResolution;
        this.lookback = lookback;
    }

    public String finnhubResolution() {
        return finnhubResolution;
    }

    public Duration lookback() {
        return lookback;
    }

    /** 외부 입력 ("1D","1W","1M","3M","1Y","5Y") → enum. */
    public static TimeFrame fromCode(String code) {
        return switch (code) {
            case "1D" -> D1;
            case "1W" -> W1;
            case "1M" -> M1;
            case "3M" -> M3;
            case "1Y" -> Y1;
            case "5Y" -> Y5;
            default -> throw new IllegalArgumentException("unknown timeframe: " + code);
        };
    }

    /** 캐시 key suffix (FE/문서 호환). */
    public String code() {
        return switch (this) {
            case D1 -> "1D";
            case W1 -> "1W";
            case M1 -> "1M";
            case M3 -> "3M";
            case Y1 -> "1Y";
            case Y5 -> "5Y";
        };
    }
}
