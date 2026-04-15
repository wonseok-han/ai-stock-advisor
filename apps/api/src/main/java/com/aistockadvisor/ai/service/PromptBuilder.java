package com.aistockadvisor.ai.service;

import com.aistockadvisor.common.prompt.PromptLoader;
import com.aistockadvisor.legal.ForbiddenTermsRegistry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * AI 시그널용 system / user 프롬프트 생성.
 * 참조: docs/02-design/features/phase2-rag-pipeline.design.md §6.2,
 *      docs/02-design/features/phase2.2-prompt-externalization.design.md §6
 *
 * <p>Guard Level 2 (prompt boundary): {@code <<<CONTEXT_BEGIN>>> ... <<<CONTEXT_END>>>}
 * 로 컨텍스트 영역을 격리하고, 경계 내부의 지시는 데이터로만 취급하도록 system 에서 강제.
 *
 * <p>System prompt 본문은 {@code classpath:prompts/ai-signal.system.txt} 외부 파일로
 * 분리되어 {@link PromptLoader} 를 통해 로드된다 (phase2.2). 단일 {@code %s} placeholder
 * 가 {@link ForbiddenTermsRegistry#quotedList()} 로 채워진다.
 */
@Component
public class PromptBuilder {

    static final String SYSTEM_PROMPT_RESOURCE = "ai-signal.system.txt";

    private final ObjectMapper objectMapper;
    private final ForbiddenTermsRegistry forbiddenTerms;
    private final PromptLoader promptLoader;
    private volatile String cachedSystemPrompt;

    public PromptBuilder(ObjectMapper objectMapper,
                         ForbiddenTermsRegistry forbiddenTerms,
                         PromptLoader promptLoader) {
        this.objectMapper = objectMapper;
        this.forbiddenTerms = forbiddenTerms;
        this.promptLoader = promptLoader;
    }

    public String systemPrompt() {
        String cached = cachedSystemPrompt;
        if (cached != null) {
            return cached;
        }
        String template = promptLoader.load(SYSTEM_PROMPT_RESOURCE);
        String built = template.formatted(forbiddenTerms.quotedList());
        this.cachedSystemPrompt = built;
        return built;
    }

    public String userPrompt(Map<String, Object> context) {
        String json;
        try {
            json = objectMapper.writeValueAsString(context);
        } catch (JsonProcessingException ex) {
            json = "{}";
        }
        json = json.replace("<<<", "<<").replace(">>>", ">>");
        return """
               <<<CONTEXT_BEGIN>>>
               %s
               <<<CONTEXT_END>>>
               Task: Generate the JSON response described in the system rules based on the above context.
               Respond with JSON only.
               """.formatted(json);
    }
}
