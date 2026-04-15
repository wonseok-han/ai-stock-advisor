package com.aistockadvisor.news.service;

import com.aistockadvisor.ai.infra.LlmClient;
import com.aistockadvisor.common.prompt.PromptLoader;
import com.aistockadvisor.legal.ForbiddenTermsRegistry;
import com.aistockadvisor.news.domain.NewsItem.Sentiment;
import com.aistockadvisor.news.infra.FinnhubNewsClient.CompanyNews;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 뉴스 영→한 번역 + 감성 분석.
 * 참조: docs/02-design/features/phase2-rag-pipeline.design.md §6.1,
 *      docs/02-design/features/phase2.2-prompt-externalization.design.md §6
 *
 * <p>Prompt: {@code classpath:prompts/news-translate.system.txt} ({@link PromptLoader} 로딩, phase2.2)<br>
 * Guard Level 2 (프롬프트 경계 마커) + Level 3 (응답 검증: 금지용어 차단).
 */
@Component
public class NewsTranslator {

    private static final Logger log = LoggerFactory.getLogger(NewsTranslator.class);

    static final String SYSTEM_PROMPT_RESOURCE = "news-translate.system.txt";

    private final LlmClient llmClient;
    private final ForbiddenTermsRegistry forbiddenTerms;
    private final ObjectMapper objectMapper;
    private final PromptLoader promptLoader;
    private volatile String cachedSystemPrompt;

    public NewsTranslator(LlmClient llmClient,
                          ForbiddenTermsRegistry forbiddenTerms,
                          ObjectMapper objectMapper,
                          PromptLoader promptLoader) {
        this.llmClient = llmClient;
        this.forbiddenTerms = forbiddenTerms;
        this.objectMapper = objectMapper;
        this.promptLoader = promptLoader;
    }

    private String systemPrompt() {
        String cached = cachedSystemPrompt;
        if (cached != null) {
            return cached;
        }
        String template = promptLoader.load(SYSTEM_PROMPT_RESOURCE);
        String built = template.formatted(forbiddenTerms.quotedList());
        this.cachedSystemPrompt = built;
        return built;
    }

    /** 단일 아이템 번역. 실패 시 null (상위에서 원문 영문 유지). */
    public Translation translate(CompanyNews news) {
        if (news == null || news.headline() == null) {
            return null;
        }
        String userPrompt = buildUserPrompt(news);
        try {
            LlmClient.LlmResult result = llmClient.generate(systemPrompt(), userPrompt,
                    com.aistockadvisor.common.metrics.LlmMetrics.FEATURE_NEWS);
            TranslateResponse parsed = objectMapper.readValue(result.content(), TranslateResponse.class);
            if (parsed.title_ko == null || parsed.summary_ko == null || parsed.sentiment == null) {
                log.warn("news-translator incomplete payload id={}", news.id());
                return null;
            }
            String combined = parsed.title_ko + " " + parsed.summary_ko;
            List<String> hits = forbiddenTerms.detect(combined);
            if (!hits.isEmpty()) {
                log.warn("news-translator forbidden detected id={} hits={}", news.id(), hits);
                return null;
            }
            Sentiment sentiment = parseSentiment(parsed.sentiment);
            return new Translation(parsed.title_ko, parsed.summary_ko, sentiment);
        } catch (Exception ex) {
            log.warn("news-translator failed id={} reason={}", news.id(), ex.getMessage());
            return null;
        }
    }

    private String buildUserPrompt(CompanyNews news) {
        String headline = Objects.requireNonNullElse(news.headline(), "");
        String summary = Objects.requireNonNullElse(news.summary(), "");
        return """
               <<<NEWS_CONTEXT_BEGIN>>>
               headline: %s
               body: %s
               <<<NEWS_CONTEXT_END>>>
               Task: Translate and summarize in Korean per the system rules.
               Respond with JSON only.
               """.formatted(escape(headline), escape(summary));
    }

    private String escape(String text) {
        return text
                .replace("<<<", "<<")
                .replace(">>>", ">>")
                .replace("\n", " ")
                .trim();
    }

    private Sentiment parseSentiment(String raw) {
        try {
            return Sentiment.valueOf(raw.trim().toUpperCase());
        } catch (Exception ex) {
            return Sentiment.NEUTRAL;
        }
    }

    public record Translation(String titleKo, String summaryKo, Sentiment sentiment) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TranslateResponse(String title_ko, String summary_ko, String sentiment) {
    }

    // Unused but kept for future jmustache/prompt-templating swap hook.
    @SuppressWarnings("unused")
    private static final Map<String, String> PROMPT_METADATA = Map.of(
            "version", "v1.0",
            "temperature", "0.2"
    );
}
