package com.aistockadvisor.ai.service;

import com.aistockadvisor.ai.domain.AiSignal.Signal;
import com.aistockadvisor.ai.domain.AiSignal.Timeframe;
import com.aistockadvisor.legal.ForbiddenTermsRegistry;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * LLM 응답 스키마 + 금지용어 검증 (4-level guard의 Level 3).
 * 참조: docs/02-design/features/phase2-rag-pipeline.design.md §7.3
 */
@Component
public class ResponseValidator {

    private static final Logger log = LoggerFactory.getLogger(ResponseValidator.class);

    private final ObjectMapper objectMapper;
    private final ForbiddenTermsRegistry forbidden;

    public ResponseValidator(ObjectMapper objectMapper, ForbiddenTermsRegistry forbidden) {
        this.objectMapper = objectMapper;
        this.forbidden = forbidden;
    }

    public Result validate(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return Result.invalid("empty response", List.of(), null);
        }
        RawSignal parsed;
        Map<String, Object> rawMap;
        try {
            parsed = objectMapper.readValue(rawJson, RawSignal.class);
            rawMap = objectMapper.readValue(rawJson, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            log.warn("validator: parse failed: {}", ex.getMessage());
            return Result.invalid("parse-failed: " + ex.getMessage(), List.of(), null);
        }

        Signal signal = parseEnum(Signal.class, parsed.signal);
        Timeframe timeframe = parseEnum(Timeframe.class, parsed.timeframe);
        if (signal == null || timeframe == null) {
            return Result.invalid("invalid enum values", List.of(), rawMap);
        }
        double confidence = parsed.confidence == null ? -1 : parsed.confidence;
        if (confidence < 0.0 || confidence > 1.0) {
            return Result.invalid("confidence out of range", List.of(), rawMap);
        }
        List<String> rationale = nonEmptyList(parsed.rationale);
        List<String> risks = nonEmptyList(parsed.risks);
        if (rationale.isEmpty() || risks.isEmpty()) {
            return Result.invalid("rationale/risks must be non-empty", List.of(), rawMap);
        }
        String summary = parsed.summary_ko == null ? "" : parsed.summary_ko.trim();
        if (summary.isBlank()) {
            return Result.invalid("summary_ko is blank", List.of(), rawMap);
        }

        StringBuilder scan = new StringBuilder();
        scan.append(summary).append(' ');
        for (String r : rationale) scan.append(r).append(' ');
        for (String r : risks) scan.append(r).append(' ');
        List<String> hits = forbidden.detect(scan.toString());
        if (!hits.isEmpty()) {
            return Result.invalid("forbidden-terms-detected", hits, rawMap);
        }

        return Result.valid(signal, confidence, timeframe, rationale, risks, summary, rawMap);
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

    public record Result(
            boolean valid,
            Signal signal,
            double confidence,
            Timeframe timeframe,
            List<String> rationale,
            List<String> risks,
            String summaryKo,
            String reason,
            List<String> forbiddenDetected,
            Map<String, Object> rawMap
    ) {
        public static Result valid(Signal s, double c, Timeframe tf, List<String> r, List<String> rk,
                                   String sum, Map<String, Object> raw) {
            return new Result(true, s, c, tf, r, rk, sum, null, List.of(), raw);
        }

        public static Result invalid(String reason, List<String> hits, Map<String, Object> raw) {
            return new Result(false, null, 0.0, null, List.of(), List.of(), null, reason,
                    hits == null ? List.of() : hits, raw == null ? Collections.emptyMap() : raw);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RawSignal(
            String signal,
            Double confidence,
            String timeframe,
            List<String> rationale,
            List<String> risks,
            String summary_ko
    ) {
    }
}
