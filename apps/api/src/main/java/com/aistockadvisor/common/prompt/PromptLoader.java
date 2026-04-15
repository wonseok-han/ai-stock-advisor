package com.aistockadvisor.common.prompt;

/**
 * classpath:prompts/{name} UTF-8 텍스트 로더.
 * 참조: docs/02-design/features/phase2.2-prompt-externalization.design.md §3
 *
 * <p>로드 결과는 인스턴스 단위로 캐시. 누락/IO 실패는 {@link IllegalStateException}.
 *
 * <p>placeholder 규칙: {@code %s} 가 0개 또는 1개. 그 외 {@code %} 는 {@code %%} 로 이스케이프
 * 되어야 함 (호출부에서 {@link String#formatted} 사용 시 안전성 보장).
 */
public interface PromptLoader {

    /**
     * classpath:prompts/{name} 텍스트 반환.
     *
     * @param name 파일명 (디렉토리 prefix 제외, 예: "ai-signal.system.txt")
     * @return UTF-8 디코딩된 텍스트
     * @throws IllegalStateException resource 누락 / IO 실패 / placeholder 검증 실패
     */
    String load(String name);
}
