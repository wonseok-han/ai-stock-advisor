package com.aistockadvisor.ai.web;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * 관리자 backfill 트리거 요청 바디.
 * 참조: docs/02-design/features/signal-accuracy.design.md §4.2
 */
public record BackfillEvaluationRequest(
        @NotNull Integer window,
        Instant since
) {}
