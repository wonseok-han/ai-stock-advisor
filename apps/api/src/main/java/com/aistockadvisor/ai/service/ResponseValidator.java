package com.aistockadvisor.ai.service;

import com.aistockadvisor.ai.domain.AiSignal.Signal;
import com.aistockadvisor.ai.domain.ImpactDirection;
import com.aistockadvisor.ai.domain.IndicatorInterpretation;
import com.aistockadvisor.ai.domain.NewsImpact;
import com.aistockadvisor.ai.domain.SignalPerspective;
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
        RawDualSignal parsed;
        Map<String, Object> rawMap;
        try {
            parsed = objectMapper.readValue(rawJson, RawDualSignal.class);
            rawMap = objectMapper.readValue(rawJson,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            log.warn("validator: parse failed: {}", ex.getMessage());
            recordValidationFailure(feature);
            return Result.invalid("parse-failed: " + ex.getMessage(), List.of(), null);
        }

        if (parsed.short_term == null || parsed.long_term == null) {
            recordValidationFailure(feature);
            return Result.invalid("missing short_term or long_term", List.of(), rawMap);
        }

        SignalPerspective shortTerm = validatePerspective(parsed.short_term, feature);
        if (shortTerm == null) {
            return Result.invalid("short_term validation failed", List.of(), rawMap);
        }

        SignalPerspective longTerm = validatePerspective(parsed.long_term, feature);
        if (longTerm == null) {
            return Result.invalid("long_term validation failed", List.of(), rawMap);
        }

        StringBuilder scan = new StringBuilder();
        appendPerspectiveText(scan, shortTerm);
        appendPerspectiveText(scan, longTerm);
        List<String> hits = forbidden.detect(scan.toString());
        if (!hits.isEmpty()) {
            recordForbiddenHit(feature, hits.size());
            return Result.invalid("forbidden-terms-detected", hits, rawMap);
        }

        return Result.valid(shortTerm, longTerm, rawMap);
    }

    private SignalPerspective validatePerspective(RawSignal raw, String feature) {
        Signal signal = parseEnum(Signal.class, raw.signal);
        if (signal == null) {
            recordValidationFailure(feature);
            return null;
        }
        double confidence = raw.confidence == null ? -1 : raw.confidence;
        if (confidence < 0.0 || confidence > 1.0) {
            recordValidationFailure(feature);
            return null;
        }
        List<String> rationale = nonEmptyList(raw.rationale);
        List<String> risks = nonEmptyList(raw.risks);
        if (rationale.isEmpty() || risks.isEmpty()) {
            recordValidationFailure(feature);
            return null;
        }
        String summary = raw.summary_ko == null ? "" : raw.summary_ko.trim();
        if (summary.isBlank()) {
            recordValidationFailure(feature);
            return null;
        }

        String beginnerExplanation = blankToNull(raw.beginner_explanation);
        List<IndicatorInterpretation> indicatorInterpretation = mapIndicators(raw.indicator_interpretation);
        List<NewsImpact> newsImpact = mapNewsImpact(raw.news_impact);
        List<String> whatToWatch = nullIfEmpty(nonEmptyList(raw.what_to_watch));

        return new SignalPerspective(
                signal, confidence, rationale, risks, summary,
                beginnerExplanation, indicatorInterpretation, newsImpact, whatToWatch
        );
    }

    private void appendPerspectiveText(StringBuilder scan, SignalPerspective p) {
        scan.append(p.summaryKo()).append(' ');
        for (String r : p.rationale()) scan.append(r).append(' ');
        for (String r : p.risks()) scan.append(r).append(' ');
        if (p.beginnerExplanation() != null) scan.append(p.beginnerExplanation()).append(' ');
        if (p.indicatorInterpretation() != null) {
            for (IndicatorInterpretation ii : p.indicatorInterpretation()) scan.append(ii.meaningKo()).append(' ');
        }
        if (p.newsImpact() != null) {
            for (NewsImpact ni : p.newsImpact()) {
                if (ni.titleKo() != null) scan.append(ni.titleKo()).append(' ');
                if (ni.reasonKo() != null) scan.append(ni.reasonKo()).append(' ');
            }
        }
        if (p.whatToWatch() != null) {
            for (String w : p.whatToWatch()) scan.append(w).append(' ');
        }
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
            SignalPerspective shortTerm,
            SignalPerspective longTerm,
            String reason,
            List<String> forbiddenDetected,
            Map<String, Object> rawMap
    ) {
        public static Result valid(SignalPerspective shortTerm, SignalPerspective longTerm,
                                   Map<String, Object> raw) {
            return new Result(true, shortTerm, longTerm, null, List.of(), raw);
        }

        public static Result invalid(String reason, List<String> hits, Map<String, Object> raw) {
            return new Result(false, null, null,
                    reason, hits == null ? List.of() : hits,
                    raw == null ? Collections.emptyMap() : raw);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RawDualSignal(
            RawSignal short_term,
            RawSignal long_term
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RawSignal(
            String signal,
            Double confidence,
            List<String> rationale,
            List<String> risks,
            String summary_ko,
            String beginner_explanation,
            List<RawIndicator> indicator_interpretation,
            List<RawNewsImpact> news_impact,
            List<String> what_to_watch
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RawIndicator(
            String indicator,
            String value,
            String meaning_ko
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RawNewsImpact(
            String title_ko,
            String impact,
            String reason_ko,
            Integer hours_ago
    ) {}
}
