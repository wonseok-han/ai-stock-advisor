package com.aistockadvisor.common.prompt;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * {@link PromptLoader} 의 classpath 기반 구현.
 * 참조: docs/02-design/features/phase2.2-prompt-externalization.design.md §3.2
 *
 * <p>{@link ResourceLoader} 로 {@code classpath:prompts/{name}} 을 읽어 UTF-8 디코딩 후
 * placeholder 검증을 거쳐 캐시. 검증·로드는 {@link #load} 첫 호출 시 1회만 수행.
 */
@Component
public class ClasspathPromptLoader implements PromptLoader {

    private static final String BASE = "prompts/";

    private final ResourceLoader resourceLoader;
    private final ConcurrentMap<String, String> cache = new ConcurrentHashMap<>();

    public ClasspathPromptLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public String load(String name) {
        return cache.computeIfAbsent(name, this::loadAndVerify);
    }

    private String loadAndVerify(String name) {
        String location = "classpath:" + BASE + name;
        Resource res = resourceLoader.getResource(location);
        if (!res.exists()) {
            throw new IllegalStateException("Prompt resource not found: " + location);
        }
        String text;
        try (InputStream in = res.getInputStream()) {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read prompt: " + location, ex);
        }
        verifyPlaceholders(name, text);
        return text;
    }

    /**
     * {@code %s} 외의 {@code %} 는 {@code %%} 로 이스케이프되어야 함.
     * {@link String#formatted} 호출 시 IllegalFormatException 폭주 방지.
     *
     * <p>알고리즘: {@code %%} 를 먼저 제거한 뒤 남은 {@code %} 위치를 훑어
     * 정확히 {@code %s} 토큰만 허용. 발견된 {@code %s} 가 1개 초과면 거부.
     */
    private void verifyPlaceholders(String name, String text) {
        String stripped = text.replace("%%", "");
        int idx = 0;
        int count = 0;
        while ((idx = stripped.indexOf('%', idx)) != -1) {
            if (idx + 1 >= stripped.length() || stripped.charAt(idx + 1) != 's') {
                throw new IllegalStateException(
                        "Invalid % token in prompt " + name + " at index " + idx
                                + " (only %s or escaped %% allowed)");
            }
            count++;
            idx += 2;
        }
        if (count > 1) {
            throw new IllegalStateException(
                    "Prompt " + name + " contains more than one %s placeholder (found " + count + ")");
        }
    }
}
