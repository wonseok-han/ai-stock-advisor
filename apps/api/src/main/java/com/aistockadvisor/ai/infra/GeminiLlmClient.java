package com.aistockadvisor.ai.infra;

import com.aistockadvisor.common.error.BusinessException;
import com.aistockadvisor.common.error.ErrorCode;
import com.aistockadvisor.common.metrics.LlmMetrics;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Gemini 1.5 Flash 기반 LlmClient 구현.
 * 참조: docs/02-design/features/phase2-rag-pipeline.design.md §5,
 *      docs/02-design/features/phase2.1-metrics-fe-refactor.design.md §4.2
 *
 * <p>Endpoint: {@code POST {baseUrl}/models/{model}:generateContent?key={apiKey}}<br>
 * {@code responseMimeType=application/json} 으로 JSON 모드 강제.
 *
 * <p>Micrometer 지표: call.count, token.total, failure.count, call.latency.
 */
@Component
public class GeminiLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiLlmClient.class);

    private final WebClient webClient;
    private final GeminiProperties props;
    private final Duration timeout;
    private final MeterRegistry meterRegistry;

    public GeminiLlmClient(GeminiProperties props, MeterRegistry meterRegistry) {
        this.props = props;
        this.meterRegistry = meterRegistry;
        this.timeout = Duration.ofMillis(props.timeoutMsOrDefault());
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) timeout.toMillis())
                .doOnConnected(conn -> conn.addHandlerLast(
                        new ReadTimeoutHandler(timeout.toMillis(), TimeUnit.MILLISECONDS)))
                .responseTimeout(timeout);
        this.webClient = WebClient.builder()
                .baseUrl(props.baseUrlOrDefault())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(c -> c.defaultCodecs().maxInMemorySize(1024 * 1024))
                .build();
    }

    @Override
    public LlmResult generate(String systemPrompt, String userPrompt) {
        return generate(systemPrompt, userPrompt, LlmMetrics.FEATURE_UNKNOWN);
    }

    @Override
    public LlmResult generate(String systemPrompt, String userPrompt, String feature) {
        String model = props.modelOrDefault();
        meterRegistry.counter(LlmMetrics.CALL_COUNT,
                LlmMetrics.TAG_FEATURE, feature,
                LlmMetrics.TAG_MODEL, model
        ).increment();

        if (props.apiKey() == null || props.apiKey().isBlank()) {
            throw new BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE, "Gemini API key is not configured.", null);
        }
        String path = "/models/" + model + ":generateContent";
        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of(
                        "role", "system",
                        "parts", List.of(Map.of("text", systemPrompt))
                ),
                "contents", List.of(
                        Map.of(
                                "role", "user",
                                "parts", List.of(Map.of("text", userPrompt))
                        )
                ),
                "generationConfig", Map.of(
                        "temperature", 0.2,
                        "topP", 0.9,
                        "maxOutputTokens", 1024,
                        "responseMimeType", "application/json"
                )
        );

        long start = System.currentTimeMillis();
        try {
            GeminiResponse resp = webClient.post()
                    .uri(b -> b.path(path).queryParam("key", props.apiKey()).build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(GeminiResponse.class)
                    .block(timeout);
            long elapsed = System.currentTimeMillis() - start;

            if (resp == null || resp.candidates() == null || resp.candidates().isEmpty()) {
                recordFailure(feature, LlmMetrics.REASON_PARSE, start);
                throw new BusinessException(ErrorCode.LLM_VALIDATION_FAILED, "Gemini returned no candidates.", null);
            }
            String text = extractText(resp);
            if (text == null || text.isBlank()) {
                recordFailure(feature, LlmMetrics.REASON_PARSE, start);
                throw new BusinessException(ErrorCode.LLM_VALIDATION_FAILED, "Gemini empty content.", null);
            }

            Integer tokensIn = resp.usageMetadata() == null ? null : resp.usageMetadata().promptTokenCount();
            Integer tokensOut = resp.usageMetadata() == null ? null : resp.usageMetadata().candidatesTokenCount();
            recordTokens(model, tokensIn, tokensOut);
            recordLatency(feature, LlmMetrics.OUTCOME_SUCCESS, start);
            return new LlmResult(text, model, tokensIn, tokensOut, elapsed);
        } catch (WebClientResponseException ex) {
            HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
            log.warn("gemini call failed status={} body={}", status, ex.getResponseBodyAsString());
            recordFailure(feature, LlmMetrics.REASON_HTTP, start);
            if (status == HttpStatus.TOO_MANY_REQUESTS) {
                throw new BusinessException(ErrorCode.UPSTREAM_RATE_LIMIT, null, null, ex);
            }
            if (status != null && status.is5xxServerError()) {
                throw new BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE, null, null, ex);
            }
            throw new BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE, null, null, ex);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("gemini timeout/io: {}", ex.getMessage());
            recordFailure(feature, LlmMetrics.REASON_TIMEOUT, start);
            throw new BusinessException(ErrorCode.UPSTREAM_TIMEOUT, null, null, ex);
        }
    }

    private void recordTokens(String model, Integer tokensIn, Integer tokensOut) {
        if (tokensIn != null && tokensIn > 0) {
            meterRegistry.counter(LlmMetrics.TOKEN_TOTAL,
                    LlmMetrics.TAG_DIRECTION, LlmMetrics.DIRECTION_INPUT,
                    LlmMetrics.TAG_MODEL, model
            ).increment(tokensIn);
        }
        if (tokensOut != null && tokensOut > 0) {
            meterRegistry.counter(LlmMetrics.TOKEN_TOTAL,
                    LlmMetrics.TAG_DIRECTION, LlmMetrics.DIRECTION_OUTPUT,
                    LlmMetrics.TAG_MODEL, model
            ).increment(tokensOut);
        }
    }

    private void recordFailure(String feature, String reason, long startMillis) {
        meterRegistry.counter(LlmMetrics.FAILURE_COUNT,
                LlmMetrics.TAG_FEATURE, feature,
                LlmMetrics.TAG_REASON, reason
        ).increment();
        recordLatency(feature, LlmMetrics.OUTCOME_FAILURE, startMillis);
    }

    private void recordLatency(String feature, String outcome, long startMillis) {
        Timer.builder(LlmMetrics.CALL_LATENCY)
                .tag(LlmMetrics.TAG_FEATURE, feature)
                .tag(LlmMetrics.TAG_OUTCOME, outcome)
                .register(meterRegistry)
                .record(Duration.ofMillis(System.currentTimeMillis() - startMillis));
    }

    private String extractText(GeminiResponse resp) {
        GeminiResponse.Candidate c = resp.candidates().get(0);
        if (c == null || c.content() == null || c.content().parts() == null || c.content().parts().isEmpty()) {
            return null;
        }
        return c.content().parts().get(0).text();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeminiResponse(List<Candidate> candidates, UsageMetadata usageMetadata) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        record Candidate(Content content, String finishReason) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Content(List<Part> parts, String role) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Part(String text) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record UsageMetadata(Integer promptTokenCount, Integer candidatesTokenCount, Integer totalTokenCount) {
        }
    }
}
