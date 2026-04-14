package com.aistockadvisor.stock.domain;

import java.time.Duration;

/**
 * 차트 타임프레임. provider 별 해상도 + 반환 bar 개수 매핑.
 * 참조: docs/02-design/features/mvp.design.md §3.2, §3.4.
 *
 * <p>OHLCV provider 는 Twelve Data (Finnhub 무료 /candle 403 우회).
 * Twelve Data interval 규격: "1min","5min","15min","30min","1h","1day","1week","1month".
 * <p>lookback 은 참고용 (provider 가 epoch-from/to 를 요구할 때 사용).
 */
public enum TimeFrame {

    D1("5min",   78,  Duration.ofDays(1)),
    W1("30min",  70,  Duration.ofDays(7)),
    M1("1day",   30,  Duration.ofDays(30)),
    M3("1day",   90,  Duration.ofDays(90)),
    Y1("1day",   260, Duration.ofDays(365)),
    Y5("1week",  260, Duration.ofDays(365L * 5));

    private final String twelveDataInterval;
    private final int outputSize;
    private final Duration lookback;

    TimeFrame(String twelveDataInterval, int outputSize, Duration lookback) {
        this.twelveDataInterval = twelveDataInterval;
        this.outputSize = outputSize;
        this.lookback = lookback;
    }

    public String twelveDataInterval() {
        return twelveDataInterval;
    }

    public int outputSize() {
        return outputSize;
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
