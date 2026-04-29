package com.nowini.ai.service;

import com.nowini.ai.domain.AiSignal;
import com.nowini.ai.domain.AiSignal.Signal;
import com.nowini.ai.domain.AiSignal.Timeframe;
import com.nowini.ai.domain.SignalPerspective;
import com.nowini.ai.infra.AiSignalAuditEntity;
import com.nowini.ai.infra.AiSignalAuditRepository;
import com.nowini.ai.infra.GeminiProperties;
import com.nowini.ai.infra.LlmClient;
import com.nowini.cache.RedisCacheAdapter;
import com.nowini.legal.Disclaimers;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AiSignalService {

    private static final Logger log = LoggerFactory.getLogger(AiSignalService.class);
    private static final TypeReference<AiSignal> CACHE_TYPE = new TypeReference<>() {};

    private final AiSignalRateLimiter rateLimiter;
    private final ContextAssembler contextAssembler;
    private final PromptBuilder promptBuilder;
    private final LlmClient llmClient;
    private final ResponseValidator validator;
    private final AiSignalAuditRepository auditRepository;
    private final RedisCacheAdapter cache;
    private final String modelName;
    private final Duration cacheTtl;

    public AiSignalService(AiSignalRateLimiter rateLimiter,
                           ContextAssembler contextAssembler,
                           PromptBuilder promptBuilder,
                           LlmClient llmClient,
                           ResponseValidator validator,
                           AiSignalAuditRepository auditRepository,
                           RedisCacheAdapter cache,
                           GeminiProperties geminiProps,
                           @Value("${app.cache.ai-signal-ttl-minutes:60}") long ttlMinutes) {
        this.rateLimiter = rateLimiter;
        this.contextAssembler = contextAssembler;
        this.promptBuilder = promptBuilder;
        this.llmClient = llmClient;
        this.validator = validator;
        this.auditRepository = auditRepository;
        this.cache = cache;
        this.modelName = geminiProps.modelOrDefault();
        this.cacheTtl = Duration.ofMinutes(ttlMinutes);
    }

    public AiSignal getSignal(String ticker) {
        String cacheKey = "ai:" + ticker + ":v3";
        AiSignal cached = cache.get(cacheKey, CACHE_TYPE);
        if (cached != null) {
            return cached;
        }

        rateLimiter.checkOrThrow();

        UUID requestId = UUID.randomUUID();
        Map<String, Object> ctx = contextAssembler.assemble(ticker);
        String systemPrompt = promptBuilder.systemPrompt();
        String userPrompt = promptBuilder.userPrompt(ctx);

        long started = System.currentTimeMillis();
        AiSignal result;
        try {
            LlmClient.LlmResult raw = llmClient.generate(systemPrompt, userPrompt,
                    com.nowini.common.metrics.LlmMetrics.FEATURE_AI_SIGNAL);
            ResponseValidator.Result validated = validator.validate(raw.content(),
                    com.nowini.common.metrics.LlmMetrics.FEATURE_AI_SIGNAL);
            if (!validated.valid()) {
                log.warn("ai-signal validation failed ticker={} reason={} hits={}",
                        ticker, validated.reason(), validated.forbiddenDetected());
                result = fallback(ticker);
                saveAudit(requestId, ticker, result, ctx, validated.rawMap(),
                        validated.forbiddenDetected(), null, true,
                        (int) (System.currentTimeMillis() - started),
                        raw.tokensIn(), raw.tokensOut());
            } else {
                result = new AiSignal(
                        ticker,
                        validated.shortTerm(),
                        validated.longTerm(),
                        Instant.now(),
                        raw.modelName(),
                        Disclaimers.AI_SIGNAL,
                        false
                );
                saveAudit(requestId, ticker, result, ctx, validated.rawMap(),
                        List.of(), buildExtendedResponse(result), false,
                        (int) (System.currentTimeMillis() - started),
                        raw.tokensIn(), raw.tokensOut());
            }
        } catch (Exception ex) {
            log.warn("ai-signal upstream failure ticker={} reason={}", ticker, ex.getMessage());
            result = fallback(ticker);
            saveAudit(requestId, ticker, result, ctx, null, List.of(), null, true,
                    (int) (System.currentTimeMillis() - started), null, null);
        }

        cache.set(cacheKey, result, cacheTtl);
        return result;
    }

    private AiSignal fallback(String ticker) {
        SignalPerspective neutral = new SignalPerspective(
                Signal.NEUTRAL,
                0.5,
                List.of("현재 충분한 데이터를 종합할 수 없어 중립 관점으로 제시합니다.",
                        "기술 지표·뉴스·가격 흐름을 추가 확인하신 뒤 참고하세요."),
                List.of("시장 변동성에 따라 단기 가격 방향이 크게 바뀔 수 있습니다.",
                        "외부 데이터/AI 응답이 일시적으로 제한되어 신뢰도가 낮습니다."),
                "일시적으로 AI 분석이 제한되어 중립(NEUTRAL) 관점으로 제공됩니다. 투자 판단 시 참고용으로만 활용해주세요.",
                null, null, null, null
        );
        return new AiSignal(ticker, neutral, neutral, Instant.now(),
                modelName, Disclaimers.AI_SIGNAL, true);
    }

    private void saveAudit(UUID requestId, String ticker, AiSignal signal,
                           Map<String, Object> ctx, Map<String, Object> rawResponse,
                           List<String> forbidden, Map<String, Object> extendedResponse,
                           boolean fallback,
                           int latencyMs, Integer tokensIn, Integer tokensOut) {
        try {
            SignalPerspective primary = signal.shortTerm();
            AiSignalAuditEntity audit = new AiSignalAuditEntity(
                    UUID.randomUUID(),
                    ticker,
                    requestId,
                    primary.signal(),
                    BigDecimal.valueOf(primary.confidence()).setScale(2, RoundingMode.HALF_UP),
                    null,
                    primary.rationale(),
                    primary.risks(),
                    primary.summaryKo(),
                    signal.modelName(),
                    ctx == null ? Map.of() : ctx,
                    rawResponse,
                    forbidden == null || forbidden.isEmpty() ? null : forbidden,
                    extendedResponse,
                    fallback,
                    latencyMs,
                    tokensIn,
                    tokensOut,
                    signal.generatedAt()
            );
            auditRepository.save(audit);
        } catch (Exception ex) {
            log.warn("audit persist failed ticker={} reason={}", ticker, ex.getMessage());
        }
    }

    private Map<String, Object> buildExtendedResponse(AiSignal signal) {
        Map<String, Object> extended = new LinkedHashMap<>();
        extended.put("short_term", perspectiveMap(signal.shortTerm()));
        extended.put("long_term", perspectiveMap(signal.longTerm()));
        return extended;
    }

    private Map<String, Object> perspectiveMap(SignalPerspective p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("signal", p.signal().name());
        m.put("confidence", p.confidence());
        m.put("summary_ko", p.summaryKo());
        if (p.beginnerExplanation() != null) m.put("beginner_explanation", p.beginnerExplanation());
        if (p.indicatorInterpretation() != null) m.put("indicator_interpretation", p.indicatorInterpretation());
        if (p.newsImpact() != null) m.put("news_impact", p.newsImpact());
        if (p.whatToWatch() != null) m.put("what_to_watch", p.whatToWatch());
        return m;
    }
}
