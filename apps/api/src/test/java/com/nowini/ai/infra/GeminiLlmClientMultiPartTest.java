package com.nowini.ai.infra;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GeminiLlmClient extractText 멀티파트 재조립 회귀 테스트.
 * <p>
 * Gemini 2.5 는 긴 응답 본문을 여러 text part 로 쪼개 보내는데, 첫 part 만 반환하면
 * JSON 이 문자열 중간에서 잘려 파싱이 실패한다("Unexpected end-of-input"). thought 가 아닌
 * text part 를 모두 이어붙여 온전한 JSON 을 복원하는지 검증한다.
 */
class GeminiLlmClientMultiPartTest {

    private MockWebServer mockWebServer;
    private GeminiLlmClient client;

    private static final String FEATURE = "market-regime";
    private static final String MODEL = "gemini-2.5-flash";

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        String baseUrl = mockWebServer.url("").toString();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        GeminiProperties props = new GeminiProperties("test-api-key", MODEL, baseUrl, 500);
        client = new GeminiLlmClient(props, new SimpleMeterRegistry());
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("본문이 여러 text part 로 쪼개져 와도 이어붙여 온전한 JSON 복원")
    void multipleTextParts_areConcatenated() {
        String body = """
                {
                  "candidates": [{
                    "content": {
                      "parts": [
                        {"text": "{\\"summary\\":\\"hel"},
                        {"text": "lo world\\"}"}
                      ],
                      "role": "model"
                    },
                    "finishReason": "STOP"
                  }],
                  "usageMetadata": {"promptTokenCount": 10, "candidatesTokenCount": 5, "totalTokenCount": 15}
                }
                """;
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body));

        var result = client.generate("system", "user", FEATURE);

        assertThat(result).isNotNull();
        assertThat(result.content()).isEqualTo("{\"summary\":\"hello world\"}");
    }

    @Test
    @DisplayName("thought part 는 건너뛰고 본문 text part 만 이어붙임")
    void thoughtPart_isSkipped() {
        String body = """
                {
                  "candidates": [{
                    "content": {
                      "parts": [
                        {"text": "내부 사고 과정...", "thought": true},
                        {"text": "{\\"a\\":"},
                        {"text": "1}"}
                      ],
                      "role": "model"
                    },
                    "finishReason": "STOP"
                  }],
                  "usageMetadata": {"promptTokenCount": 10, "candidatesTokenCount": 5, "totalTokenCount": 15}
                }
                """;
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body));

        var result = client.generate("system", "user", FEATURE);

        assertThat(result).isNotNull();
        assertThat(result.content()).isEqualTo("{\"a\":1}");
    }
}
