package com.aistockadvisor.legal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 금지용어 레지스트리.
 * 참조: docs/02-design/features/phase2-rag-pipeline.design.md §7.1
 *
 * <p>구조: {@code legal/forbidden-terms.json} 을 로드해 ko/en 단어 리스트를 메모리에 보관.
 * 4-level guard 의 Level 1 (constants) 및 Level 3 (validator) 에서 사용.
 */
@Component
public class ForbiddenTermsRegistry {

    private static final Logger log = LoggerFactory.getLogger(ForbiddenTermsRegistry.class);

    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final String path;

    private List<String> allLower = List.of();

    public ForbiddenTermsRegistry(ObjectMapper objectMapper,
                                  ResourceLoader resourceLoader,
                                  @Value("${app.legal.forbidden-terms-path:classpath:legal/forbidden-terms.json}") String path) {
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.path = path;
    }

    @PostConstruct
    void load() throws IOException {
        Resource resource = resourceLoader.getResource(path);
        try (InputStream in = resource.getInputStream()) {
            ForbiddenTermsFile file = objectMapper.readValue(in, ForbiddenTermsFile.class);
            List<String> merged = new ArrayList<>();
            if (file.terms() != null) {
                if (file.terms().get("ko") != null) {
                    merged.addAll(file.terms().get("ko"));
                }
                if (file.terms().get("en") != null) {
                    merged.addAll(file.terms().get("en"));
                }
            }
            this.allLower = merged.stream()
                    .filter(s -> s != null && !s.isBlank())
                    .map(s -> s.toLowerCase(Locale.ROOT))
                    .distinct()
                    .toList();
            log.info("forbidden-terms loaded: version={} count={}", file.version(), allLower.size());
        }
    }

    /** 검출된 금지용어 목록. 없으면 빈 리스트. 대소문자 무시. */
    public List<String> detect(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String lower = text.toLowerCase(Locale.ROOT);
        List<String> hits = new ArrayList<>();
        for (String term : allLower) {
            if (lower.contains(term)) {
                hits.add(term);
            }
        }
        return hits;
    }

    public boolean containsAny(String text) {
        return !detect(text).isEmpty();
    }

    public int size() {
        return allLower.size();
    }

    /**
     * 프롬프트 삽입용: {@code "a", "b", "c"} 형태의 쌍따옴표 CSV.
     * Java 소스에 literal 을 두지 않고 런타임에 주입하여 Level 4 CI grep 오탐을 방지.
     */
    public String quotedList() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < allLower.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append('"').append(allLower.get(i)).append('"');
        }
        return sb.toString();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ForbiddenTermsFile(
            String version,
            String updatedAt,
            String description,
            Map<String, List<String>> terms
    ) {
    }
}
