package com.aistockadvisor.ai.domain;

import java.time.Instant;
import java.util.List;

/**
 * AI 시그널 응답 DTO.
 * 참조: docs/02-design/features/phase2-rag-pipeline.design.md §3.1
 */
public record AiSignal(
        String ticker,
        Signal signal,
        double confidence,
        Timeframe timeframe,
        List<String> rationale,
        List<String> risks,
        String summaryKo,
        Instant generatedAt,
        String modelName,
        String disclaimer,
        boolean fallback
) {
    public enum Signal { STRONG_BUY, BUY, NEUTRAL, SELL, STRONG_SELL }
    public enum Timeframe { SHORT, MID, LONG }
}
