package com.aistockadvisor.ai.domain;

import java.util.List;
import java.util.Set;

/**
 * 정합도 평가 윈도우 (일 단위).
 * 참조: docs/02-design/features/signal-accuracy.design.md §3.1
 */
public record EvaluationWindow(int days) {

    public static final EvaluationWindow W7 = new EvaluationWindow(7);
    public static final EvaluationWindow W30 = new EvaluationWindow(30);

    /** API·설정에서 허용되는 윈도우 화이트리스트. */
    public static final Set<Integer> ALLOWED = Set.of(7, 30);

    public static final List<EvaluationWindow> DEFAULTS = List.of(W7, W30);

    public EvaluationWindow {
        if (!ALLOWED.contains(days)) {
            throw new IllegalArgumentException("Unsupported window: " + days + " (allowed: " + ALLOWED + ")");
        }
    }

    public short asShort() {
        return (short) days;
    }
}
