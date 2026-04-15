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
 * Gemini 2.5 Flash 기반 LlmClient 구현.
 * 참조: docs/02-design/features/phase2-rag-pipeline.design.md §5,
 *      docs/02-design/features/phase2.1-metrics-fe-refactor.design.md §4.2,
 *      docs/02-design/features/phase2.2-prompt-externalization.design.md §7
 *
 * <p>Endpoint: {@code POST {baseUrl}/models/{model}:generateContent?key={apiKey}}<br>
 * {@code responseMimeType=application/json} 으로 JSON 모드 강제.
 *
 * <p>Gemini 2.5 호환: {@code thinkingConfig.thinkingBudget=0} 으로 thinking mode 비활성화.
 * 활성 상태에서는 thinking 토큰이 {@code maxOutputTokens} 예산을 소진해 실제 응답 JSON 이
 * 중간 절단되는 현상 방지 ({@code finishReason=MAX_TOKENS}). extractText 는 방어적으로
 * {@code thought=true} part 를 스킵하고 첫 유효 text 를 반환.
 *
 * <p>Phase 2.2 — transient retry 1회 (총 최대 {@value #MAX_ATTEMPTS} 시도, 고정 backoff
 * {@value #RETRY_BACKOFF_MS}ms). 분류 매트릭스: 5xx / 429 / timeout / io = retryable,
 * 4xx (429 제외) / parse / validation / MAX_TOKENS = non-retryable.
 *
 * <p>Micrometer 지표: call.count(호출 단위 1회), token.total, failure.count(시도별),
 * call.latency, retry.count(outcome=success|exhausted).<br>
 * 로그 태그: 모든 WARN 에 feature / model 포함.
 */
@Component
public class GeminiLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiLlmClient.class);

    static final int MAX_ATTEMPTS = 2;
    static final long RETRY_BACKOFF_MS = 250L;

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

        BusinessException lastTransient = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                LlmResult result = callOnce(systemPrompt, userPrompt, feature, model);
                if (attempt > 1) {
                    meterRegistry.counter(LlmMetrics.RETRY_COUNT,
                            LlmMetrics.TAG_FEATURE, feature,
                            LlmMetrics.TAG_MODEL, model,
                            LlmMetrics.TAG_OUTCOME, LlmMetrics.OUTCOME_SUCCESS
                    ).increment();
                    log.info("gemini retry success feature={} model={} attempt={}", feature, model, attempt);
                }
                return result;
            } catch (RetryableUpstreamException ex) {
                lastTransient = ex.cause();
                if (attempt < MAX_ATTEMPTS) {
                    sleepBackoff();
                    continue;
                }
                meterRegistry.counter(LlmMetrics.RETRY_COUNT,
                        LlmMetrics.TAG_FEATURE, feature,
                        LlmMetrics.TAG_MODEL, model,
                        LlmMetrics.TAG_OUTCOME, LlmMetrics.OUTCOME_EXHAUSTED
                ).increment();
                log.warn("gemini retry exhausted feature={} model={} attempts={}",
                        feature, model, MAX_ATTEMPTS);
                throw lastTransient;
            }
        }
        // unreachable — loop either returns or throws
        throw lastTransient;
    }

    private LlmResult callOnce(String systemPrompt, String userPrompt, String feature, String model) {
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
                        "maxOutputTokens", 2048,
                        "responseMimeType", "application/json",
                        // Gemini 2.5 호환: thinking 토큰이 maxOutputTokens 예산을 소진해
                        // 실제 응답 JSON 이 중간 절단되는 현상 방지. 0=disabled.
                        "thinkingConfig", Map.of("thinkingBudget", 0)
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
                log.warn("gemini call no-candidates feature={} model={}", feature, model);
                recordFailure(feature, LlmMetrics.REASON_PARSE, start);
                throw new BusinessException(ErrorCode.LLM_VALIDATION_FAILED, "Gemini returned no candidates.", null);
            }
            GeminiResponse.Candidate candidate = resp.candidates().get(0);
            if ("MAX_TOKENS".equals(candidate == null ? null : candidate.finishReason())) {
                log.warn("gemini call truncated feature={} model={} finishReason=MAX_TOKENS", feature, model);
            }
            String text = extractText(resp);
            if (text == null || text.isBlank()) {
                log.warn("gemini call empty-content feature={} model={} finishReason={}",
                        feature, model, candidate == null ? null : candidate.finishReason());
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
            log.warn("gemini call failed feature={} model={} status={} body={}",
                    feature, model, status, ex.getResponseBodyAsString());
            recordFailure(feature, LlmMetrics.REASON_HTTP, start);
            if (status == HttpStatus.TOO_MANY_REQUESTS) {
                throw new RetryableUpstreamException(
                        new BusinessException(ErrorCode.UPSTREAM_RATE_LIMIT, null, null, ex));
            }
            if (status != null && status.is5xxServerError()) {
                throw new RetryableUpstreamException(
                        new BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE, null, null, ex));
            }
            // 4xx (429 제외) → non-retryable
            throw new BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE, null, null, ex);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RetryableUpstreamException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("gemini call timeout/io feature={} model={} msg={}", feature, model, ex.getMessage());
            recordFailure(feature, LlmMetrics.REASON_TIMEOUT, start);
            throw new RetryableUpstreamException(
                    new BusinessException(ErrorCode.UPSTREAM_TIMEOUT, null, null, ex));
        }
    }

    private void sleepBackoff() {
        try {
            Thread.sleep(RETRY_BACKOFF_MS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Internal marker — callOnce 가 transient(재시도 가능) 실패임을 generate 루프에 신호.
     * 원본 {@link BusinessException} 을 wrapping 하여 retry 소진 시 그대로 throw 한다.
     */
    private static final class RetryableUpstreamException extends RuntimeException {
        private final BusinessException cause;

        RetryableUpstreamException(BusinessException cause) {
            super(cause);
            this.cause = cause;
        }

        BusinessException cause() {
            return cause;
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
        // Gemini 2.5: parts 배열에 thought part 가 섞일 수 있음. thought=true 는 스킵.
        for (GeminiResponse.Part p : c.content().parts()) {
            if (Boolean.TRUE.equals(p.thought())) continue;
            if (p.text() != null && !p.text().isBlank()) return p.text();
        }
        return null;
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
        record Part(String text, Boolean thought) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record UsageMetadata(Integer promptTokenCount, Integer candidatesTokenCount, Integer totalTokenCount) {
        }
    }
}
