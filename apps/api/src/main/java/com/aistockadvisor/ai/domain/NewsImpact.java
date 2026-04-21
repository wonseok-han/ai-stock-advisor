package com.aistockadvisor.ai.domain;

/**
 * 개별 뉴스가 시그널에 미친 영향 요약.
 * 참조: docs/02-design/features/ai-analysis-deepening.design.md §3.1
 */
public record NewsImpact(
        String titleKo,
        ImpactDirection impact,
        String reasonKo,
        Integer hoursAgo
) {
}
