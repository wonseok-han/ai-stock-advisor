package com.aistockadvisor.sec.service;

import com.aistockadvisor.ai.infra.LlmClient;
import com.aistockadvisor.common.metrics.LlmMetrics;
import com.aistockadvisor.common.prompt.PromptLoader;
import com.aistockadvisor.sec.domain.SecFiling;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class SecFilingSummarizer {

    private static final Logger log = LoggerFactory.getLogger(SecFilingSummarizer.class);
    private static final TypeReference<List<Map<String, String>>> LIST_TYPE = new TypeReference<>() {};

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final PromptLoader promptLoader;

    public SecFilingSummarizer(LlmClient llmClient, ObjectMapper objectMapper,
                               PromptLoader promptLoader) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.promptLoader = promptLoader;
    }

    public List<String> summarize(List<SecFiling> filings) {
        if (filings == null || filings.isEmpty()) return List.of();

        boolean hasContent = filings.stream().anyMatch(f -> f.contentSummary() != null);
        if (!hasContent) return filings.stream().map(f -> (String) null).toList();

        try {
            String system = promptLoader.load("sec-filing-summary.system.txt");
            String user = buildUserPrompt(filings);
            LlmClient.LlmResult result = llmClient.generate(system, user,
                    LlmMetrics.FEATURE_SEC_SUMMARY);

            List<Map<String, String>> parsed = objectMapper.readValue(result.content(), LIST_TYPE);
            if (parsed.size() != filings.size()) {
                log.warn("sec summary size mismatch: expected={} got={}", filings.size(), parsed.size());
                return filings.stream().map(f -> (String) null).toList();
            }
            return parsed.stream()
                    .map(m -> m.getOrDefault("summary_ko", null))
                    .toList();
        } catch (Exception ex) {
            log.warn("sec filing summarization failed: {}", ex.getMessage());
            return filings.stream().map(f -> (String) null).toList();
        }
    }

    private String buildUserPrompt(List<SecFiling> filings) {
        try {
            List<Map<String, Object>> input = filings.stream().map(f -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("form", f.form());
                m.put("event_category", f.eventCategory());
                m.put("content_summary", f.contentSummary());
                return m;
            }).toList();
            return objectMapper.writeValueAsString(input);
        } catch (Exception ex) {
            return "[]";
        }
    }
}
