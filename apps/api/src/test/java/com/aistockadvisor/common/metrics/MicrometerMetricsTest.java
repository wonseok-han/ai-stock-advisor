package com.aistockadvisor.common.metrics;

import com.aistockadvisor.ai.service.ResponseValidator;
import com.aistockadvisor.legal.ForbiddenTermsRegistry;
import com.aistockadvisor.legal.LegalGuardFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Micrometer 관측성 지표 주입 검증 (T-5 ~ T-7).
 * 참조: docs/02-design/features/phase2.1-metrics-fe-refactor.design.md §5.1
 *
 * <p>ResponseValidator / LegalGuardFilter 에 주입된 counter 가 예상 tag 와 함께
 * 증가하는지 {@link SimpleMeterRegistry} 로 직접 확인한다. Gemini 호출 경로
 * (T-1~T-4, T-8) 는 외부 WebClient 가 필요해 단위 테스트 대신 수동 smoke + Actuator 엔드포인트로 검증.
 */
class MicrometerMetricsTest {

    private SimpleMeterRegistry registry;
    private ObjectMapper mapper;
    private ForbiddenTermsRegistry forbidden;

    @BeforeEach
    void setUp() throws Exception {
        registry = new SimpleMeterRegistry();
        mapper = new ObjectMapper();
        forbidden = new ForbiddenTermsRegistry(
                mapper, new DefaultResourceLoader(), "classpath:legal/forbidden-terms.json");
        Method load = ForbiddenTermsRegistry.class.getDeclaredMethod("load");
        load.setAccessible(true);
        load.invoke(forbidden);
    }

    @Test
    @DisplayName("T-5 ResponseValidator parse 실패 시 failure.count{reason=validation} +1")
    void parseFailureIncrementsValidationCounter() {
        ResponseValidator validator = new ResponseValidator(mapper, forbidden, registry);
        validator.validate("not a json at all {", "ai-signal");

        double failures = registry.counter(LlmMetrics.FAILURE_COUNT,
                LlmMetrics.TAG_FEATURE, "ai-signal",
                LlmMetrics.TAG_REASON, LlmMetrics.REASON_VALIDATION).count();
        assertThat(failures).isEqualTo(1.0);
    }

    @Test
    @DisplayName("T-6 ResponseValidator 금지용어 검출 → forbidden.hit{layer=validator} + failure{reason=forbidden}")
    void forbiddenTermIncrementsHitAndFailureCounter() {
        ResponseValidator validator = new ResponseValidator(mapper, forbidden, registry);
        String payloadWithForbidden = "{\"signal\":\"BUY\",\"confidence\":0.9,\"timeframe\":\"SHORT\","
                + "\"rationale\":[\"지금 매수 추천합니다.\"],\"risks\":[\"시장 변동성\"],"
                + "\"summary_ko\":\"매수 추천합니다.\"}";
        validator.validate(payloadWithForbidden, "ai-signal");

        double hits = registry.counter(LlmMetrics.FORBIDDEN_HIT,
                LlmMetrics.TAG_LAYER, LlmMetrics.LAYER_VALIDATOR,
                LlmMetrics.TAG_FEATURE, "ai-signal").count();
        double failures = registry.counter(LlmMetrics.FAILURE_COUNT,
                LlmMetrics.TAG_FEATURE, "ai-signal",
                LlmMetrics.TAG_REASON, LlmMetrics.REASON_FORBIDDEN).count();
        assertThat(hits).isGreaterThanOrEqualTo(1.0);
        assertThat(failures).isEqualTo(1.0);
    }

    @Test
    @DisplayName("T-7 LegalGuardFilter 금지용어 치환 시 forbidden.hit{layer=filter} 증가")
    void legalGuardFilterIncrementsFilterHitCounter() throws Exception {
        LegalGuardFilter filter = new LegalGuardFilter(forbidden, mapper, registry);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/stocks/AAPL/ai-signal");
        req.setRequestURI("/api/v1/stocks/AAPL/ai-signal");
        MockHttpServletResponse res = new MockHttpServletResponse();

        FilterChain chain = (request, response) -> {
            HttpServletResponse r = (HttpServletResponse) response;
            r.setContentType(MediaType.APPLICATION_JSON_VALUE);
            String body = "{\"ticker\":\"AAPL\",\"summaryKo\":\"지금 매수 추천합니다.\"}";
            r.getOutputStream().write(body.getBytes());
        };
        filter.doFilter(req, res, chain);

        double filterHits = registry.counter(LlmMetrics.FORBIDDEN_HIT,
                LlmMetrics.TAG_LAYER, LlmMetrics.LAYER_FILTER).count();
        assertThat(filterHits).isGreaterThanOrEqualTo(1.0);
    }

    @Test
    @DisplayName("금지용어 미검출 시 forbidden.hit counter 증가 없음")
    void cleanResponseDoesNotIncrementForbiddenHit() throws Exception {
        LegalGuardFilter filter = new LegalGuardFilter(forbidden, mapper, registry);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/stocks/AAPL/ai-signal");
        req.setRequestURI("/api/v1/stocks/AAPL/ai-signal");
        MockHttpServletResponse res = new MockHttpServletResponse();

        FilterChain chain = (request, response) -> {
            HttpServletResponse r = (HttpServletResponse) response;
            r.setContentType(MediaType.APPLICATION_JSON_VALUE);
            String body = "{\"ticker\":\"AAPL\",\"summaryKo\":\"참고용 중립 분석입니다.\"}";
            r.getOutputStream().write(body.getBytes());
        };
        filter.doFilter(req, res, chain);

        double filterHits = registry.counter(LlmMetrics.FORBIDDEN_HIT,
                LlmMetrics.TAG_LAYER, LlmMetrics.LAYER_FILTER).count();
        assertThat(filterHits).isEqualTo(0.0);
    }
}
