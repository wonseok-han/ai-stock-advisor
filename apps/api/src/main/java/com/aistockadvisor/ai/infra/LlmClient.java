package com.aistockadvisor.ai.infra;

/**
 * LLM 호출 추상화. 공급자 교체(Gemini ↔ Claude ↔ OpenAI) 포인트.
 * 참조: docs/02-design/features/phase2-rag-pipeline.design.md §5
 *
 * <p>구현체는 JSON 모드(또는 엄격 프롬프트)로 문자열을 돌려주어야 하며, 파싱/검증은 상위 레이어 책임.
 */
public interface LlmClient {

    /**
     * 시스템 + 유저 프롬프트 기반 생성. JSON 문자열 반환 (`{ ... }`).
     *
     * @throws com.aistockadvisor.common.error.BusinessException upstream timeout/unavailable/rate limit
     */
    LlmResult generate(String systemPrompt, String userPrompt);

    /**
     * {@link #generate(String, String)} 와 동일하되 Micrometer {@code feature} tag 를 지정.
     * 참조: docs/02-design/features/phase2.1-metrics-fe-refactor.design.md §4.2
     * 기본 구현은 tag 무시 — 구현체가 override 하여 관측성 향상.
     */
    default LlmResult generate(String systemPrompt, String userPrompt, String feature) {
        return generate(systemPrompt, userPrompt);
    }

    /** 호출 결과 + 메타데이터 (감사 로그용). */
    record LlmResult(
            String content,
            String modelName,
            Integer tokensIn,
            Integer tokensOut,
            long latencyMs
    ) {
    }
}
