package com.nowini.ai.domain;

import java.math.BigDecimal;

/**
 * AI 시그널 방향 정합도 평가 결과.
 * 참조: docs/02-design/features/signal-accuracy.design.md §3.1
 *
 * <p>Signal (5-class) 을 Direction (3-class) 으로 매핑해 실제 가격 변동 방향과 비교한다.
 * hit = predicted.equals(actual).
 */
public record SignalOutcome(
        Direction predicted,
        Direction actual,
        BigDecimal changePct,
        boolean hit
) {
    public enum Direction { UP, FLAT, DOWN }
}
