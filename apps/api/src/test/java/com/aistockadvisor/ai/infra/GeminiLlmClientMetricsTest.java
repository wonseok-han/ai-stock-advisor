package com.aistockadvisor.ai.infra;

import com.aistockadvisor.common.metrics.LlmMetrics;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Gemini 호출 경로 Micrometer 지표 자동 검증 (T-1 ~ T-4, T-8).
 * 참조: docs/02-design/features/phase2.1-metrics-fe-refactor.design.md §5.1
 *
 * <p>MockWebServer 로 외부 HTTP 의존성을 제거하고, GeminiLlmClient 를 실제 생성자로
 * 조립하여 카운터·타이머 기록 여부를 {@link SimpleMeterRegistry} 로 직접 확인한다.
 */
class GeminiLlmClientMetricsTest {

    private MockWebServer mockWebServer;
    private SimpleMeterRegistry registry;
    private GeminiLlmClient client;

    private static final String FEATURE = "ai-signal";
    private static final String MODEL = "gemini-2.5-flash";

    /** 정상 응답 JSON (GeminiResponse 레코드 구조에 맞춤) */
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
        // 끝의 '/' 제거 (GeminiLlmClient 는 baseUrl 에 path 를 붙이므로)
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        GeminiProperties props = new GeminiProperties(
                "test-api-key",
                MODEL,
                baseUrl,
                3000
        );
        client = new GeminiLlmClient(props, registry);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    // -------------------------------------------------------------------------
    // T-1: 정상 호출 → llm.call.count{feature, model} +1
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("T-1 정상 호출 → llm.call.count{feature,model} +1")
    void normalCall_incrementsCallCount() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(SUCCESS_BODY));

        client.generate("system", "user", FEATURE);

        double count = registry.counter(LlmMetrics.CALL_COUNT,
                LlmMetrics.TAG_FEATURE, FEATURE,
                LlmMetrics.TAG_MODEL, MODEL).count();
        assertThat(count).isEqualTo(1.0);
    }

    // -------------------------------------------------------------------------
    // T-2: token 사용량 기록 → llm.token.total{direction=input} + {direction=output}
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("T-2 token 사용량 → llm.token.total input(100) + output(50) 기록")
    void normalCall_recordsTokenCountsBothDirections() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(SUCCESS_BODY));

        client.generate("system", "user", FEATURE);

        double inputTokens = registry.counter(LlmMetrics.TOKEN_TOTAL,
                LlmMetrics.TAG_DIRECTION, LlmMetrics.DIRECTION_INPUT,
                LlmMetrics.TAG_MODEL, MODEL).count();
        double outputTokens = registry.counter(LlmMetrics.TOKEN_TOTAL,
                LlmMetrics.TAG_DIRECTION, LlmMetrics.DIRECTION_OUTPUT,
                LlmMetrics.TAG_MODEL, MODEL).count();

        assertThat(inputTokens).isEqualTo(100.0);
        assertThat(outputTokens).isEqualTo(50.0);
    }

    // -------------------------------------------------------------------------
    // T-3: HTTP 4xx/5xx → llm.failure.count{feature, reason=http} +1
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("T-3 HTTP 4xx → llm.failure.count{reason=http} +1")
    void http4xxResponse_incrementsHttpFailureCount() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"error\":{\"code\":400,\"message\":\"Bad Request\"}}"));

        assertThatThrownBy(() -> client.generate("system", "user", FEATURE))
                .isInstanceOf(com.aistockadvisor.common.error.BusinessException.class);

        double failures = registry.counter(LlmMetrics.FAILURE_COUNT,
                LlmMetrics.TAG_FEATURE, FEATURE,
                LlmMetrics.TAG_REASON, LlmMetrics.REASON_HTTP).count();
        assertThat(failures).isEqualTo(1.0);
    }

    // -------------------------------------------------------------------------
    // T-4: timeout → llm.failure.count{feature, reason=timeout} +1
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("T-4 WebClient timeout → llm.failure.count{reason=timeout} +1")
    void timeout_incrementsTimeoutFailureCount() {
        // MockWebServer 에 응답 없이 연결 직후 닫으면 ReactorNetty ReadTimeout 발생
        mockWebServer.enqueue(new MockResponse()
                .setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.NO_RESPONSE));

        assertThatThrownBy(() -> client.generate("system", "user", FEATURE))
                .isInstanceOf(com.aistockadvisor.common.error.BusinessException.class);

        double timeoutFailures = registry.counter(LlmMetrics.FAILURE_COUNT,
                LlmMetrics.TAG_FEATURE, FEATURE,
                LlmMetrics.TAG_REASON, LlmMetrics.REASON_TIMEOUT).count();
        assertThat(timeoutFailures).isEqualTo(1.0);
    }

    // -------------------------------------------------------------------------
    // T-8: latency Timer → success/failure 양쪽에서 1회 이상 기록
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("T-8 latency Timer → success outcome 에서 1회 이상 기록")
    void normalCall_recordsLatencyTimerSuccess() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(SUCCESS_BODY));

        client.generate("system", "user", FEATURE);

        Collection<Timer> timers = registry.find(LlmMetrics.CALL_LATENCY)
                .tag(LlmMetrics.TAG_FEATURE, FEATURE)
                .tag(LlmMetrics.TAG_OUTCOME, LlmMetrics.OUTCOME_SUCCESS)
                .timers();
        assertThat(timers).isNotEmpty();
        assertThat(timers.iterator().next().count()).isGreaterThanOrEqualTo(1L);
    }

    // -------------------------------------------------------------------------
    // T-9: MAX_TOKENS truncation → content 비어있으면 parse 실패로 분류
    // Gemini 2.5 호환: thinking 토큰이 maxOutputTokens 소진 시 parts 가 빈 경우 방어
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("T-9 MAX_TOKENS finishReason + empty parts → llm.failure.count{reason=parse} +1")
    void maxTokensTruncation_incrementsParseFailure() {
        String truncatedBody = """
                {
                  "candidates": [{
                    "content": {"parts": [], "role": "model"},
                    "finishReason": "MAX_TOKENS"
                  }],
                  "usageMetadata": {
                    "promptTokenCount": 100,
                    "candidatesTokenCount": 0,
                    "totalTokenCount": 100
                  }
                }
                """;
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(truncatedBody));

        assertThatThrownBy(() -> client.generate("system", "user", FEATURE))
                .isInstanceOf(com.aistockadvisor.common.error.BusinessException.class);

        double parseFailures = registry.counter(LlmMetrics.FAILURE_COUNT,
                LlmMetrics.TAG_FEATURE, FEATURE,
                LlmMetrics.TAG_REASON, LlmMetrics.REASON_PARSE).count();
        assertThat(parseFailures).isEqualTo(1.0);
    }

    // -------------------------------------------------------------------------
    // T-10: thought part 스킵 → 유효 text 가 있는 part 를 선택
    // Gemini 2.5 호환: thinking part(thought=true) 가 앞에 오더라도 실제 JSON 파트 반환
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("T-10 thought=true part 스킵 → 유효 text 반환 + success 기록")
    void thoughtPart_isSkippedAndNextValidTextReturned() {
        String thoughtBody = """
                {
                  "candidates": [{
                    "content": {
                      "parts": [
                        {"text": "reasoning...", "thought": true},
                        {"text": "{\\"signal\\":\\"BUY\\"}"}
                      ],
                      "role": "model"
                    },
                    "finishReason": "STOP"
                  }],
                  "usageMetadata": {
                    "promptTokenCount": 80,
                    "candidatesTokenCount": 20,
                    "totalTokenCount": 100
                  }
                }
                """;
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(thoughtBody));

        LlmClient.LlmResult result = client.generate("system", "user", FEATURE);

        assertThat(result.content()).contains("BUY");
        double callCount = registry.counter(LlmMetrics.CALL_COUNT,
                LlmMetrics.TAG_FEATURE, FEATURE,
                LlmMetrics.TAG_MODEL, MODEL).count();
        assertThat(callCount).isEqualTo(1.0);
    }

    @Test
    @DisplayName("T-8 latency Timer → failure outcome 에서 1회 이상 기록")
    void failureCall_recordsLatencyTimerFailure() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"error\":{\"code\":500,\"message\":\"Internal Server Error\"}}"));

        assertThatThrownBy(() -> client.generate("system", "user", FEATURE))
                .isInstanceOf(com.aistockadvisor.common.error.BusinessException.class);

        Collection<Timer> timers = registry.find(LlmMetrics.CALL_LATENCY)
                .tag(LlmMetrics.TAG_FEATURE, FEATURE)
                .tag(LlmMetrics.TAG_OUTCOME, LlmMetrics.OUTCOME_FAILURE)
                .timers();
        assertThat(timers).isNotEmpty();
        assertThat(timers.iterator().next().count()).isGreaterThanOrEqualTo(1L);
    }
}
