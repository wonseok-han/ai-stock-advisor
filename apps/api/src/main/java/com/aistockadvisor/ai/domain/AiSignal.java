package com.aistockadvisor.ai.domain;

import java.time.Instant;
import java.util.List;

/**
 * AI 시그널 응답 DTO.
 * 참조:
 *  - docs/02-design/features/phase2-rag-pipeline.design.md §3.1 (v1)
 *  - docs/02-design/features/ai-analysis-deepening.design.md §3.1 (v2 확장)
 *
 * v2 확장 필드(beginnerExplanation, indicatorInterpretation, newsImpact, whatToWatch)는
 * 모두 nullable — LLM 이 일부 필드를 누락해도 기존 기능은 동작해야 하므로 graceful degradation.
 */
public record AiSignal(
        String ticker,
        Signal signal,
        double confidence,
        Timeframe timeframe,
        List<String> rationale,
        List<String> risks,
        String summaryKo,
        String beginnerExplanation,
        List<IndicatorInterpretation> indicatorInterpretation,
        List<NewsImpact> newsImpact,
        List<String> whatToWatch,
        Instant generatedAt,
        String modelName,
        String disclaimer,
        boolean fallback
) {
    public enum Signal { STRONG_BUY, BUY, NEUTRAL, SELL, STRONG_SELL }
    public enum Timeframe { SHORT, MID, LONG }
}
