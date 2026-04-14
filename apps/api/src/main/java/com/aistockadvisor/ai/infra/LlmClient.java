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
