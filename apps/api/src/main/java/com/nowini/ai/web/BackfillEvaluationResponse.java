package com.nowini.ai.web;

/**
 * 관리자 backfill 트리거 응답.
 * 참조: docs/02-design/features/signal-accuracy.design.md §4.2
 */
public record BackfillEvaluationResponse(
        boolean scheduled,
        long candidateCount,
        int batchSize
) {}
