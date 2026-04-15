package com.aistockadvisor.ai.infra;

import com.aistockadvisor.common.error.BusinessException;
import com.aistockadvisor.common.error.ErrorCode;
import com.aistockadvisor.common.metrics.LlmMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * GeminiLlmClient 재시도 루프 단위 테스트 (R-1 ~ R-4).
 * 참조: docs/02-design/features/phase2.2-prompt-externalization.design.md §7, §8.2
 *
 * <p>분류 매트릭스 검증:
 * <ul>
 *   <li>R-1: 5xx → 재시도 → 200 success → retry.count{outcome=success} +1</li>
 *   <li>R-2: 429 → 재시도 → 200 success → retry.count{outcome=success} +1</li>
 *   <li>R-3: 5xx 2회 연속 → exhausted → retry.count{outcome=exhausted} +1, BusinessException UPSTREAM_UNAVAILABLE</li>
 *   <li>R-4: 4xx (400) → 재시도 안함 → retry.count 변화 없음, BusinessException 즉시 발생</li>
 * </ul>
 */
class GeminiLlmClientRetryTest {

    private MockWebServer mockWebServer;
    private SimpleMeterRegistry registry;
    private GeminiLlmClient client;

    private static final String FEATURE = "ai-signal";
    private static final String MODEL = "gemini-2.5-flash";

    private static final String SUCCESS_BODY = """
            {
              "candidates": [{
                "content": {
                  "parts": [{"text": "{\\"signal\\":\\"HOLD\\"}"}],
                  "role": "model"
                },
                "finishReason": "STOP"
              }],
              "usageMetadata": {
                "promptTokenCount": 100,
                "candidatesTokenCount": 50,
                "totalTokenCount": 150
              }
            }
            """;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        registry = new SimpleMeterRegistry();

        String baseUrl = mockWebServer.url("").toString();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        GeminiProperties props = new GeminiProperties(
                "test-api-key",
                MODEL,
                baseUrl,
                500
        );
        client = new GeminiLlmClient(props, registry);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    // -------------------------------------------------------------------------
    // R-1: 5xx → retry → 200 success
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("R-1 5xx 후 재시도 성공 → retry.count{outcome=success} +1")
    void serverError_then_success_recordsRetrySuccess() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(503).setBody("upstream"));
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(SUCCESS_BODY));

        var result = client.generate("system", "user", FEATURE);

        assertThat(result).isNotNull();
        double retrySuccess = registry.counter(LlmMetrics.RETRY_COUNT,
                LlmMetrics.TAG_FEATURE, FEATURE,
                LlmMetrics.TAG_MODEL, MODEL,
                LlmMetrics.TAG_OUTCOME, LlmMetrics.OUTCOME_SUCCESS).count();
        double retryExhausted = registry.counter(LlmMetrics.RETRY_COUNT,
                LlmMetrics.TAG_FEATURE, FEATURE,
                LlmMetrics.TAG_MODEL, MODEL,
                LlmMetrics.TAG_OUTCOME, LlmMetrics.OUTCOME_EXHAUSTED).count();
        assertThat(retrySuccess).isEqualTo(1.0);
        assertThat(retryExhausted).isEqualTo(0.0);
    }

    // -------------------------------------------------------------------------
    // R-2: 429 → retry → 200 success
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("R-2 429 후 재시도 성공 → retry.count{outcome=success} +1")
    void rateLimit_then_success_recordsRetrySuccess() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(429).setBody("rate limit"));
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(SUCCESS_BODY));

        var result = client.generate("system", "user", FEATURE);

        assertThat(result).isNotNull();
        double retrySuccess = registry.counter(LlmMetrics.RETRY_COUNT,
                LlmMetrics.TAG_FEATURE, FEATURE,
                LlmMetrics.TAG_MODEL, MODEL,
                LlmMetrics.TAG_OUTCOME, LlmMetrics.OUTCOME_SUCCESS).count();
        assertThat(retrySuccess).isEqualTo(1.0);
    }

    // -------------------------------------------------------------------------
    // R-3: 5xx 2회 연속 → exhausted
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("R-3 5xx 2회 연속 → retry.count{outcome=exhausted} +1, UPSTREAM_UNAVAILABLE 발생")
    void serverError_twice_recordsExhausted() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(503).setBody("upstream-1"));
        mockWebServer.enqueue(new MockResponse().setResponseCode(503).setBody("upstream-2"));

        assertThatThrownBy(() -> client.generate("system", "user", FEATURE))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).code())
                        .isEqualTo(ErrorCode.UPSTREAM_UNAVAILABLE));

        double retryExhausted = registry.counter(LlmMetrics.RETRY_COUNT,
                LlmMetrics.TAG_FEATURE, FEATURE,
                LlmMetrics.TAG_MODEL, MODEL,
                LlmMetrics.TAG_OUTCOME, LlmMetrics.OUTCOME_EXHAUSTED).count();
        double httpFailures = registry.counter(LlmMetrics.FAILURE_COUNT,
                LlmMetrics.TAG_FEATURE, FEATURE,
                LlmMetrics.TAG_REASON, LlmMetrics.REASON_HTTP).count();
        assertThat(retryExhausted).isEqualTo(1.0);
        assertThat(httpFailures).isEqualTo(2.0);
    }

    // -------------------------------------------------------------------------
    // R-4: 4xx (non-retryable) → 즉시 실패, retry.count 변화 없음
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("R-4 4xx (400) → 재시도 안함, retry.count 0 유지")
    void clientError_doesNotRetry() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(400)
                .setBody("{\"error\":{\"code\":400,\"message\":\"Bad Request\"}}"));

        assertThatThrownBy(() -> client.generate("system", "user", FEATURE))
                .isInstanceOf(BusinessException.class);

        double retrySuccess = registry.counter(LlmMetrics.RETRY_COUNT,
                LlmMetrics.TAG_FEATURE, FEATURE,
                LlmMetrics.TAG_MODEL, MODEL,
                LlmMetrics.TAG_OUTCOME, LlmMetrics.OUTCOME_SUCCESS).count();
        double retryExhausted = registry.counter(LlmMetrics.RETRY_COUNT,
                LlmMetrics.TAG_FEATURE, FEATURE,
                LlmMetrics.TAG_MODEL, MODEL,
                LlmMetrics.TAG_OUTCOME, LlmMetrics.OUTCOME_EXHAUSTED).count();
        double httpFailures = registry.counter(LlmMetrics.FAILURE_COUNT,
                LlmMetrics.TAG_FEATURE, FEATURE,
                LlmMetrics.TAG_REASON, LlmMetrics.REASON_HTTP).count();
        assertThat(retrySuccess).isEqualTo(0.0);
        assertThat(retryExhausted).isEqualTo(0.0);
        assertThat(httpFailures).isEqualTo(1.0);
        // 두 번째 enqueue 가 없으므로 retry 가 발생했다면 ConnectException 등 다른 오류로 폭주.
        // 통과 = retry 가 발생하지 않았음을 간접 보증.
        assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
    }
}
