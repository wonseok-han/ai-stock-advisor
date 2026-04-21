package com.aistockadvisor.ai.service;

import com.aistockadvisor.ai.domain.AiSignal.Signal;
import com.aistockadvisor.ai.domain.AiSignal.Timeframe;
import com.aistockadvisor.ai.domain.ImpactDirection;
import com.aistockadvisor.ai.domain.IndicatorInterpretation;
import com.aistockadvisor.ai.domain.NewsImpact;
import com.aistockadvisor.common.metrics.LlmMetrics;
import com.aistockadvisor.legal.ForbiddenTermsRegistry;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * LLM 응답 스키마 + 금지용어 검증 (4-level guard의 Level 3).
 * 참조:
 *  - docs/02-design/features/phase2-rag-pipeline.design.md §7.3
 *  - docs/02-design/features/phase2.1-metrics-fe-refactor.design.md §4.3
 *  - docs/02-design/features/ai-analysis-deepening.design.md §4.2 (v2 옵셔널 필드)
 *
 * <p>v2 확장: beginner_explanation / indicator_interpretation / news_impact / what_to_watch 는
 * 모두 nullable. 존재할 경우 forbidden 스캔 대상에 포함하되, 누락되어도 valid 로 간주.
 */
@Component
public class ResponseValidator {

    private static final Logger log = LoggerFactory.getLogger(ResponseValidator.class);

    private final ObjectMapper objectMapper;
    private final ForbiddenTermsRegistry forbidden;
    private final MeterRegistry meterRegistry;

    public ResponseValidator(ObjectMapper objectMapper,
                             ForbiddenTermsRegistry forbidden,
                             MeterRegistry meterRegistry) {
        this.objectMapper = objectMapper;
        this.forbidden = forbidden;
        this.meterRegistry = meterRegistry;
    }

    public Result validate(String rawJson) {
        return validate(rawJson, LlmMetrics.FEATURE_AI_SIGNAL);
    }

    public Result validate(String rawJson, String feature) {
        if (rawJson == null || rawJson.isBlank()) {
            recordValidationFailure(feature);
            return Result.invalid("empty response", List.of(), null);
        }
        RawSignal parsed;
        Map<String, Object> rawMap;
        try {
            parsed = objectMapper.readValue(rawJson, RawSignal.class);
            rawMap = objectMapper.readValue(rawJson, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            log.warn("validator: parse failed: {}", ex.getMessage());
            recordValidationFailure(feature);
            return Result.invalid("parse-failed: " + ex.getMessage(), List.of(), null);
        }

        Signal signal = parseEnum(Signal.class, parsed.signal);
        Timeframe timeframe = parseEnum(Timeframe.class, parsed.timeframe);
        if (signal == null || timeframe == null) {
            recordValidationFailure(feature);
            return Result.invalid("invalid enum values", List.of(), rawMap);
        }
        double confidence = parsed.confidence == null ? -1 : parsed.confidence;
        if (confidence < 0.0 || confidence > 1.0) {
            recordValidationFailure(feature);
            return Result.invalid("confidence out of range", List.of(), rawMap);
        }
        List<String> rationale = nonEmptyList(parsed.rationale);
        List<String> risks = nonEmptyList(parsed.risks);
        if (rationale.isEmpty() || risks.isEmpty()) {
            recordValidationFailure(feature);
            return Result.invalid("rationale/risks must be non-empty", List.of(), rawMap);
        }
        String summary = parsed.summary_ko == null ? "" : parsed.summary_ko.trim();
        if (summary.isBlank()) {
            recordValidationFailure(feature);
            return Result.invalid("summary_ko is blank", List.of(), rawMap);
        }

        // v2 옵셔널 필드 — 누락/빈 값이면 null 로 두고, 존재할 때만 정규화.
        String beginnerExplanation = blankToNull(parsed.beginner_explanation);
        List<IndicatorInterpretation> indicatorInterpretation = mapIndicators(parsed.indicator_interpretation);
        List<NewsImpact> newsImpact = mapNewsImpact(parsed.news_impact);
        List<String> whatToWatch = nullIfEmpty(nonEmptyList(parsed.what_to_watch));

        StringBuilder scan = new StringBuilder();
        scan.append(summary).append(' ');
        for (String r : rationale) scan.append(r).append(' ');
        for (String r : risks) scan.append(r).append(' ');
        if (beginnerExplanation != null) scan.append(beginnerExplanation).append(' ');
        if (indicatorInterpretation != null) {
            for (IndicatorInterpretation ii : indicatorInterpretation) scan.append(ii.meaningKo()).append(' ');
        }
        if (newsImpact != null) {
            for (NewsImpact ni : newsImpact) {
                if (ni.titleKo() != null) scan.append(ni.titleKo()).append(' ');
                if (ni.reasonKo() != null) scan.append(ni.reasonKo()).append(' ');
            }
        }
        if (whatToWatch != null) {
            for (String w : whatToWatch) scan.append(w).append(' ');
        }
        List<String> hits = forbidden.detect(scan.toString());
        if (!hits.isEmpty()) {
            recordForbiddenHit(feature, hits.size());
            return Result.invalid("forbidden-terms-detected", hits, rawMap);
        }

        return Result.valid(signal, confidence, timeframe, rationale, risks, summary,
                beginnerExplanation, indicatorInterpretation, newsImpact, whatToWatch, rawMap);
    }

    private void recordValidationFailure(String feature) {
        meterRegistry.counter(LlmMetrics.FAILURE_COUNT,
                LlmMetrics.TAG_FEATURE, feature,
                LlmMetrics.TAG_REASON, LlmMetrics.REASON_VALIDATION
        ).increment();
    }

    private void recordForbiddenHit(String feature, int count) {
        meterRegistry.counter(LlmMetrics.FORBIDDEN_HIT,
                LlmMetrics.TAG_LAYER, LlmMetrics.LAYER_VALIDATOR,
                LlmMetrics.TAG_FEATURE, feature
        ).increment(count);
        meterRegistry.counter(LlmMetrics.FAILURE_COUNT,
                LlmMetrics.TAG_FEATURE, feature,
                LlmMetrics.TAG_REASON, LlmMetrics.REASON_FORBIDDEN
        ).increment();
    }

    private <E extends Enum<E>> E parseEnum(Class<E> clazz, String raw) {
        if (raw == null) return null;
        try {
            return Enum.valueOf(clazz, raw.trim().toUpperCase());
        } catch (Exception ex) {
            return null;
        }
    }

    private List<String> nonEmptyList(List<String> src) {
        if (src == null) return List.of();
        return src.stream().filter(s -> s != null && !s.isBlank()).toList();
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static <T> List<T> nullIfEmpty(List<T> list) {
        return (list == null || list.isEmpty()) ? null : list;
    }

    private List<IndicatorInterpretation> mapIndicators(List<RawIndicator> src) {
        if (src == null || src.isEmpty()) return null;
        List<IndicatorInterpretation> out = new ArrayList<>(src.size());
        for (RawIndicator ri : src) {
            if (ri == null) continue;
            String indicator = blankToNull(ri.indicator);
            String value = blankToNull(ri.value);
            String meaning = blankToNull(ri.meaning_ko);
            if (indicator == null || meaning == null) continue;
            out.add(new IndicatorInterpretation(indicator, value == null ? "" : value, meaning));
        }
        return out.isEmpty() ? null : out;
    }

    private List<NewsImpact> mapNewsImpact(List<RawNewsImpact> src) {
        if (src == null || src.isEmpty()) return null;
        List<NewsImpact> out = new ArrayList<>(src.size());
        for (RawNewsImpact rn : src) {
            if (rn == null) continue;
            String titleKo = blankToNull(rn.title_ko);
            String reasonKo = blankToNull(rn.reason_ko);
            ImpactDirection impact = parseEnum(ImpactDirection.class, rn.impact);
            if (titleKo == null || reasonKo == null || impact == null) continue;
            out.add(new NewsImpact(titleKo, impact, reasonKo, rn.hours_ago));
        }
        return out.isEmpty() ? null : out;
    }

    public record Result(
            boolean valid,
            Signal signal,
            double confidence,
            Timeframe timeframe,
            List<String> rationale,
            List<String> risks,
            String summaryKo,
            String beginnerExplanation,
            List<IndicatorInterpretation> indicatorInterpretation,
            List<NewsImpact> newsImpact,
            List<String> whatToWatch,
            String reason,
            List<String> forbiddenDetected,
            Map<String, Object> rawMap
    ) {
        public static Result valid(Signal s, double c, Timeframe tf, List<String> r, List<String> rk,
                                   String sum,
                                   String beginnerExplanation,
                                   List<IndicatorInterpretation> indicatorInterpretation,
                                   List<NewsImpact> newsImpact,
                                   List<String> whatToWatch,
                                   Map<String, Object> raw) {
            return new Result(true, s, c, tf, r, rk, sum,
                    beginnerExplanation, indicatorInterpretation, newsImpact, whatToWatch,
                    null, List.of(), raw);
        }

        public static Result invalid(String reason, List<String> hits, Map<String, Object> raw) {
            return new Result(false, null, 0.0, null, List.of(), List.of(), null,
                    null, null, null, null,
                    reason, hits == null ? List.of() : hits,
                    raw == null ? Collections.emptyMap() : raw);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RawSignal(
            String signal,
            Double confidence,
            String timeframe,
            List<String> rationale,
            List<String> risks,
            String summary_ko,
            String beginner_explanation,
            List<RawIndicator> indicator_interpretation,
            List<RawNewsImpact> news_impact,
            List<String> what_to_watch
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RawIndicator(
            String indicator,
            String value,
            String meaning_ko
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RawNewsImpact(
            String title_ko,
            String impact,
            String reason_ko,
            Integer hours_ago
    ) {
    }
}
