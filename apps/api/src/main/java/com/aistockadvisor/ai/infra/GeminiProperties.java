package com.aistockadvisor.ai.infra;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Gemini API 설정. application.yml: app.external.gemini.*
 */
@ConfigurationProperties(prefix = "app.external.gemini")
public record GeminiProperties(
        String apiKey,
        String model,
        String baseUrl,
        Integer timeoutMs
) {
    public String baseUrlOrDefault() {
        return baseUrl == null || baseUrl.isBlank()
                ? "https://generativelanguage.googleapis.com/v1beta"
                : baseUrl;
    }

    public String modelOrDefault() {
        return model == null || model.isBlank() ? "gemini-1.5-flash" : model;
    }

    public int timeoutMsOrDefault() {
        return timeoutMs == null || timeoutMs < 1000 ? 8000 : timeoutMs;
    }
}
