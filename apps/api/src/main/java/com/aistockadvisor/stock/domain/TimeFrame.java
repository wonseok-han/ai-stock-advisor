package com.aistockadvisor.stock.domain;

/**
 * 차트 타임프레임. provider 별 해상도 + 반환 bar 개수 매핑.
 * 참조: docs/02-design/features/phase4.5-improvements.design.md §4.2.
 *
 * <p>Phase 4.5 변경: D1(intraday)만 TwelveData API, W1~Y5는 DB 일봉 기반.
 * <p>OHLCV provider 는 Twelve Data (intraday) + Yahoo Finance (daily DB).
 */
public enum TimeFrame {

    D1("5min",    78,    1, false),
    W1("1day",     5,    7,  true),
    M1("1day",    22,   30,  true),
    M3("1day",    66,   90,  true),
    Y1("1day",   252,  365,  true),
    Y5("1day",  1260, 1825,  true);

    private final String twelveDataInterval;
    private final int outputSize;
    /** lookback 기간 (일 단위). LocalDate.minusDays() 에 직접 사용. */
    private final long lookbackDays;
    private final boolean dbBacked;

    TimeFrame(String twelveDataInterval, int outputSize, long lookbackDays, boolean dbBacked) {
        this.twelveDataInterval = twelveDataInterval;
        this.outputSize = outputSize;
        this.lookbackDays = lookbackDays;
        this.dbBacked = dbBacked;
    }

    public String twelveDataInterval() {
        return twelveDataInterval;
    }

    public int outputSize() {
        return outputSize;
    }

    public long lookbackDays() {
        return lookbackDays;
    }

    /** true이면 DB 일봉 기반 조회, false��면 TwelveData API 실시간. */
    public boolean dbBacked() {
        return dbBacked;
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
