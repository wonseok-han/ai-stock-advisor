package com.nowini.ai.domain;

import java.time.Instant;

public record AiSignal(
        String ticker,
        SignalPerspective shortTerm,
        SignalPerspective longTerm,
        Instant generatedAt,
        String modelName,
        String disclaimer,
        boolean fallback
) {
    // v2 하위 호환: audit 엔티티가 기존 레코드에 사용
    public enum Signal { STRONG_BUY, BUY, NEUTRAL, SELL, STRONG_SELL }
    public enum Timeframe { SHORT, MID, LONG }
}
