package com.aistockadvisor.ai.service;

import com.aistockadvisor.ai.domain.AiSignal;
import com.aistockadvisor.ai.domain.AiSignal.Signal;
import com.aistockadvisor.ai.domain.AiSignal.Timeframe;
import com.aistockadvisor.ai.infra.AiSignalAuditEntity;
import com.aistockadvisor.ai.infra.AiSignalAuditRepository;
import com.aistockadvisor.ai.infra.GeminiProperties;
import com.aistockadvisor.ai.infra.LlmClient;
import com.aistockadvisor.cache.RedisCacheAdapter;
import com.aistockadvisor.legal.Disclaimers;
import com.aistockadvisor.stock.domain.TimeFrame;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AI 시그널 오케스트레이션.
 * 참조: docs/02-design/features/phase2-rag-pipeline.design.md §5, §6.2, §7
 *
 * <p>Flow: Redis 1h 캐시 → rate limit → ContextAssembler → PromptBuilder →
 * LlmClient → ResponseValidator → Audit insert → 응답. 실패 시 neutral fallback (audit fallback=true).
 */
@Service
public class AiSignalService {

    private static final Logger log = LoggerFactory.getLogger(AiSignalService.class);
    private static final TypeReference<AiSignal> CACHE_TYPE = new TypeReference<>() {
    };

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

    public AiSignal getSignal(String ticker, TimeFrame timeframe) {
        String cacheKey = "ai:" + ticker + ":" + (timeframe == null ? "1D" : timeframe.code()) + ":v1";
        AiSignal cached = cache.get(cacheKey, CACHE_TYPE);
        if (cached != null) {
            return cached;
        }

        rateLimiter.checkOrThrow();

        UUID requestId = UUID.randomUUID();
        Map<String, Object> ctx = contextAssembler.assemble(ticker, timeframe);
        String systemPrompt = promptBuilder.systemPrompt();
        String userPrompt = promptBuilder.userPrompt(ctx);

        long started = System.currentTimeMillis();
        AiSignal result;
        try {
            LlmClient.LlmResult raw = llmClient.generate(systemPrompt, userPrompt);
            ResponseValidator.Result validated = validator.validate(raw.content());
            if (!validated.valid()) {
                log.warn("ai-signal validation failed ticker={} reason={} hits={}",
                        ticker, validated.reason(), validated.forbiddenDetected());
                result = fallback(ticker);
                saveAudit(requestId, ticker, result, ctx, validated.rawMap(),
                        validated.forbiddenDetected(), true,
                        (int) (System.currentTimeMillis() - started),
                        raw.tokensIn(), raw.tokensOut());
            } else {
                result = new AiSignal(
                        ticker,
                        validated.signal(),
                        validated.confidence(),
                        validated.timeframe(),
                        validated.rationale(),
                        validated.risks(),
                        validated.summaryKo(),
                        Instant.now(),
                        raw.modelName(),
                        Disclaimers.AI_SIGNAL,
                        false
                );
                saveAudit(requestId, ticker, result, ctx, validated.rawMap(),
                        List.of(), false,
                        (int) (System.currentTimeMillis() - started),
                        raw.tokensIn(), raw.tokensOut());
            }
        } catch (Exception ex) {
            log.warn("ai-signal upstream failure ticker={} reason={}", ticker, ex.getMessage());
            result = fallback(ticker);
            saveAudit(requestId, ticker, result, ctx, null, List.of(), true,
                    (int) (System.currentTimeMillis() - started), null, null);
        }

        cache.set(cacheKey, result, cacheTtl);
        return result;
    }

    private AiSignal fallback(String ticker) {
        return new AiSignal(
                ticker,
                Signal.NEUTRAL,
                0.5,
                Timeframe.MID,
                List.of("현재 충분한 데이터를 종합할 수 없어 중립 관점으로 제시합니다.",
                        "기술 지표·뉴스·가격 흐름을 추가 확인하신 뒤 참고하세요."),
                List.of("시장 변동성에 따라 단기 가격 방향이 크게 바뀔 수 있습니다.",
                        "외부 데이터/AI 응답이 일시적으로 제한되어 신뢰도가 낮습니다."),
                "일시적으로 AI 분석이 제한되어 중립(NEUTRAL) 관점으로 제공됩니다. 투자 판단 시 참고용으로만 활용해주세요.",
                Instant.now(),
                modelName,
                Disclaimers.AI_SIGNAL,
                true
        );
    }

    private void saveAudit(UUID requestId, String ticker, AiSignal signal,
                           Map<String, Object> ctx, Map<String, Object> rawResponse,
                           List<String> forbidden, boolean fallback,
                           int latencyMs, Integer tokensIn, Integer tokensOut) {
        try {
            AiSignalAuditEntity audit = new AiSignalAuditEntity(
                    UUID.randomUUID(),
                    ticker,
                    requestId,
                    signal.signal(),
                    BigDecimal.valueOf(signal.confidence()).setScale(2, RoundingMode.HALF_UP),
                    signal.timeframe(),
                    signal.rationale(),
                    signal.risks(),
                    signal.summaryKo(),
                    signal.modelName(),
                    ctx == null ? Map.of() : ctx,
                    rawResponse,
                    forbidden == null || forbidden.isEmpty() ? null : forbidden,
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
}
