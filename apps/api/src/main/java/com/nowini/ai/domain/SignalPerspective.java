package com.nowini.ai.domain;

import com.nowini.ai.domain.AiSignal.Signal;

import java.util.List;

public record SignalPerspective(
        Signal signal,
        double confidence,
        List<String> rationale,
        List<String> risks,
        String summaryKo,
        String beginnerExplanation,
        List<IndicatorInterpretation> indicatorInterpretation,
        List<NewsImpact> newsImpact,
        List<String> whatToWatch
) {
}
