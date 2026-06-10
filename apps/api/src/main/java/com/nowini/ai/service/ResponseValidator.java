package com.nowini.ai.service;

import com.nowini.ai.domain.AiSignal.Signal;
import com.nowini.ai.domain.ImpactDirection;
import com.nowini.ai.domain.IndicatorInterpretation;
import com.nowini.ai.domain.NewsImpact;
import com.nowini.ai.domain.SignalPerspective;
import com.nowini.ai.domain.TimingFactor;
import com.nowini.ai.domain.TimingVerdict;
import com.nowini.common.metrics.LlmMetrics;
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
    private final MeterRegistry meterRegistry;

    public ResponseValidator(ObjectMapper objectMapper,
                             MeterRegistry meterRegistry) {
        this.objectMapper = objectMapper;
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

        TimingVerdict timing = validateTiming(parsed.timing);

        return Result.valid(shortTerm, longTerm, timing, rawMap);
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

    private TimingVerdict validateTiming(RawTiming raw) {
        if (raw == null) return null;
        TimingVerdict.Verdict verdict = parseEnum(TimingVerdict.Verdict.class, raw.verdict);
        if (verdict == null) return null;
        if (raw.score == null || raw.score < 0 || raw.score > 100) return null;
        List<TimingFactor> met = mapFactors(raw.factors_met);
        List<TimingFactor> unmet = mapFactors(raw.factors_unmet);
        if (met.isEmpty() && unmet.isEmpty()) return null;
        if (met.size() + unmet.size() < 4) return null;
        String summary = blankToNull(raw.summary_ko);
        if (summary == null) return null;
        String disclaimer = raw.disclaimer_ko != null ? raw.disclaimer_ko
                : "진입 조건의 기술적 충족 여부를 정리한 것으로, 투자 판단은 본인의 책임입니다.";
        return new TimingVerdict(verdict, raw.score, met, unmet, summary, disclaimer);
    }

    private List<TimingFactor> mapFactors(List<RawFactor> src) {
        if (src == null || src.isEmpty()) return List.of();
        List<TimingFactor> out = new ArrayList<>(src.size());
        for (RawFactor rf : src) {
            if (rf == null) continue;
            String factor = blankToNull(rf.factor);
            String detail = blankToNull(rf.detail);
            if (factor == null || detail == null) continue;
            int weight = rf.weight != null ? rf.weight : 10;
            out.add(new TimingFactor(factor, detail, weight));
        }
        return out;
    }

    private void recordValidationFailure(String feature) {
        meterRegistry.counter(LlmMetrics.FAILURE_COUNT,
                LlmMetrics.TAG_FEATURE, feature,
                LlmMetrics.TAG_REASON, LlmMetrics.REASON_VALIDATION
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
            TimingVerdict timing,
            String reason,
            List<String> forbiddenDetected,
            Map<String, Object> rawMap
    ) {
        public static Result valid(SignalPerspective shortTerm, SignalPerspective longTerm,
                                   TimingVerdict timing, Map<String, Object> raw) {
            return new Result(true, shortTerm, longTerm, timing, null, List.of(), raw);
        }

        public static Result invalid(String reason, List<String> hits, Map<String, Object> raw) {
            return new Result(false, null, null, null,
                    reason, hits == null ? List.of() : hits,
                    raw == null ? Collections.emptyMap() : raw);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RawDualSignal(
            RawSignal short_term,
            RawSignal long_term,
            RawTiming timing
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RawTiming(
            String verdict,
            Integer score,
            List<RawFactor> factors_met,
            List<RawFactor> factors_unmet,
            String summary_ko,
            String disclaimer_ko
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RawFactor(
            String factor,
            String detail,
            Integer weight
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
