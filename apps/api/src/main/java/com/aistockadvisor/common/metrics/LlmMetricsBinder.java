package com.aistockadvisor.common.metrics;

import com.aistockadvisor.ai.infra.GeminiProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

/**
 * 애플리케이션 기동 시 LLM 관련 Micrometer 지표를 초기값(0)으로 등록.
 *
 * <p>{@link MeterBinder} 를 구현하면 Spring Boot Actuator 가 {@link MeterRegistry} 준비
 * 완료 후 {@link #bindTo(MeterRegistry)} 를 자동 호출해 Prometheus 스크레이프 대상에
 * 포함시킨다. LLM 호출이 아직 한 건도 없어도 {@code llm_call_count_total} 등의 시계열이
 * {@code /actuator/prometheus} 에 노출되어 §5.2 / §9 Acceptance Criteria를 충족한다.
 *
 * <p>참조: docs/02-design/features/phase2.1-metrics-fe-refactor.design.md §4.3, §5.2
 */
@Component
public class LlmMetricsBinder implements MeterBinder {

    private final GeminiProperties geminiProps;

    public LlmMetricsBinder(GeminiProperties geminiProps) {
        this.geminiProps = geminiProps;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        String model = geminiProps.modelOrDefault();

        // call.count — feature 별 기본 시리즈 (ai-signal, unknown)
        for (String feature : new String[]{LlmMetrics.FEATURE_AI_SIGNAL, LlmMetrics.FEATURE_UNKNOWN}) {
            registry.counter(LlmMetrics.CALL_COUNT,
                    LlmMetrics.TAG_FEATURE, feature,
                    LlmMetrics.TAG_MODEL, model);
        }

        // token.total — input / output 양방향
        for (String direction : new String[]{LlmMetrics.DIRECTION_INPUT, LlmMetrics.DIRECTION_OUTPUT}) {
            registry.counter(LlmMetrics.TOKEN_TOTAL,
                    LlmMetrics.TAG_DIRECTION, direction,
                    LlmMetrics.TAG_MODEL, model);
        }

        // failure.count — timeout, http, parse, validation, forbidden
        for (String reason : new String[]{
                LlmMetrics.REASON_TIMEOUT,
                LlmMetrics.REASON_HTTP,
                LlmMetrics.REASON_PARSE,
                LlmMetrics.REASON_VALIDATION,
                LlmMetrics.REASON_FORBIDDEN}) {
            registry.counter(LlmMetrics.FAILURE_COUNT,
                    LlmMetrics.TAG_FEATURE, LlmMetrics.FEATURE_AI_SIGNAL,
                    LlmMetrics.TAG_REASON, reason);
        }

        // forbidden.hit.count — validator / filter
        for (String layer : new String[]{LlmMetrics.LAYER_VALIDATOR, LlmMetrics.LAYER_FILTER}) {
            registry.counter(LlmMetrics.FORBIDDEN_HIT,
                    LlmMetrics.TAG_LAYER, layer,
                    LlmMetrics.TAG_FEATURE, LlmMetrics.FEATURE_AI_SIGNAL);
        }
    }
}
