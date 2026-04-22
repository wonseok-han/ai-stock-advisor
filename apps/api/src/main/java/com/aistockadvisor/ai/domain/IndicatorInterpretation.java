package com.aistockadvisor.ai.domain;

/**
 * 기술 지표 해석 — 초보자를 위해 값이 무엇을 뜻하는지 한 줄로 설명.
 * 참조: docs/02-design/features/ai-analysis-deepening.design.md §3.1
 */
public record IndicatorInterpretation(
        String indicator,
        String value,
        String meaningKo
) {
}
