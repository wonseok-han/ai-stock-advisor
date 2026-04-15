package com.aistockadvisor.common.prompt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ClasspathPromptLoader 단위 테스트 (L-1 ~ L-3).
 * 참조: docs/02-design/features/phase2.2-prompt-externalization.design.md §8.2
 *
 * <p>테스트 픽스처는 {@code apps/api/src/test/resources/prompts/} 에 위치.
 */
class ClasspathPromptLoaderTest {

    private ClasspathPromptLoader loader;

    @BeforeEach
    void setUp() {
        loader = new ClasspathPromptLoader(new DefaultResourceLoader());
    }

    // -------------------------------------------------------------------------
    // L-1: happy path — UTF-8 멀티바이트 + %s placeholder 정상 로드
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("L-1 happy: 정상 파일 로드 + UTF-8 멀티바이트(한국어) 보존 + %s 1개 허용")
    void happyPath_loadsTextAndPreservesUtf8() {
        String text = loader.load("test-happy.txt");

        assertThat(text).contains("한국어 멀티바이트 테스트 prompt.");
        assertThat(text).contains("%s");
        assertThat(text).endsWith("end.\n");
    }

    // -------------------------------------------------------------------------
    // L-2: missing — 존재하지 않는 파일
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("L-2 missing: 존재하지 않는 파일 → IllegalStateException + 경로 메시지")
    void missingResource_throwsIllegalStateException() {
        assertThatThrownBy(() -> loader.load("does-not-exist.txt"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("classpath:prompts/does-not-exist.txt");
    }

    // -------------------------------------------------------------------------
    // L-3: invalid placeholder — %d 등 %s 가 아닌 % 토큰
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("L-3 invalid: %d 토큰 포함 → IllegalStateException")
    void invalidPlaceholder_throwsIllegalStateException() {
        assertThatThrownBy(() -> loader.load("test-invalid-token.txt"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid % token");
    }
}
