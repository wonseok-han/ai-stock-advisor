package com.aistockadvisor.common.metrics;

/**
 * LLM 관측성 Micrometer 지표 이름 + tag key 상수.
 * 참조: docs/02-design/features/phase2.1-metrics-fe-refactor.design.md §3
 *
 * <p>명명 규칙: dot-notation 소문자, prefix {@code llm.} 고정.
 * Prometheus export 시 {@code _} 로 자동 변환됨.
 */
public final class LlmMetrics {

    public static final String CALL_COUNT = "llm.call.count";
    public static final String TOKEN_TOTAL = "llm.token.total";
    public static final String FAILURE_COUNT = "llm.failure.count";
    public static final String FORBIDDEN_HIT = "llm.forbidden.hit.count";
    public static final String CALL_LATENCY = "llm.call.latency";
    /** phase2.2 retry attempt counter — outcome=success(재시도 후 성공) | exhausted(최대 시도 후 실패). */
    public static final String RETRY_COUNT = "llm.retry.count";

    public static final String TAG_FEATURE = "feature";
    public static final String TAG_MODEL = "model";
    public static final String TAG_DIRECTION = "direction";
    public static final String TAG_REASON = "reason";
    public static final String TAG_LAYER = "layer";
    public static final String TAG_OUTCOME = "outcome";

    public static final String FEATURE_AI_SIGNAL = "ai-signal";
    public static final String FEATURE_NEWS = "news";
    public static final String FEATURE_UNKNOWN = "unknown";

    public static final String DIRECTION_INPUT = "input";
    public static final String DIRECTION_OUTPUT = "output";

    public static final String REASON_TIMEOUT = "timeout";
    public static final String REASON_HTTP = "http";
    public static final String REASON_PARSE = "parse";
    public static final String REASON_VALIDATION = "validation";
    public static final String REASON_FORBIDDEN = "forbidden";

    public static final String LAYER_VALIDATOR = "validator";
    public static final String LAYER_FILTER = "filter";

    public static final String OUTCOME_SUCCESS = "success";
    public static final String OUTCOME_FAILURE = "failure";
    /** phase2.2 — retry 루프가 최대 시도 횟수에 도달했지만 끝내 성공하지 못한 케이스. */
    public static final String OUTCOME_EXHAUSTED = "exhausted";

    private LlmMetrics() {
    }
}
