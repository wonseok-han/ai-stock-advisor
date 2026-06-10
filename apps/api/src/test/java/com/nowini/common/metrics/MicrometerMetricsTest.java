package com.nowini.common.metrics;

import com.nowini.ai.service.ResponseValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Micrometer 관측성 지표 주입 검증 (T-5).
 * 참조: docs/02-design/features/phase2.1-metrics-fe-refactor.design.md §5.1
 *
 * <p>ResponseValidator 에 주입된 counter 가 예상 tag 와 함께 증가하는지
 * {@link SimpleMeterRegistry} 로 직접 확인한다. Gemini 호출 경로
 * (T-1~T-4, T-8) 는 외부 WebClient 가 필요해 단위 테스트 대신 수동 smoke + Actuator 엔드포인트로 검증.
 */
class MicrometerMetricsTest {

    private SimpleMeterRegistry registry;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        mapper = new ObjectMapper();
    }

    @Test
    @DisplayName("T-5 ResponseValidator parse 실패 시 failure.count{reason=validation} +1")
    void parseFailureIncrementsValidationCounter() {
        ResponseValidator validator = new ResponseValidator(mapper, registry);
        validator.validate("not a json at all {", "ai-signal");

        double failures = registry.counter(LlmMetrics.FAILURE_COUNT,
                LlmMetrics.TAG_FEATURE, "ai-signal",
                LlmMetrics.TAG_REASON, LlmMetrics.REASON_VALIDATION).count();
        assertThat(failures).isEqualTo(1.0);
    }
}
