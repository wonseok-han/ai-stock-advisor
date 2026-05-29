package com.nowini.market.infra;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * FRED(Federal Reserve Economic Data) API 설정. application.yml: app.external.fred.*
 * <p>
 * 버핏지수(NCBEILQ027S/GDP), 장단기 금리차(T10Y2Y), 신용스프레드(BAMLH0A0HYM2) 등
 * 매크로 시리즈 조회에 사용. api-key가 비어 있으면 비활성.
 */
@ConfigurationProperties(prefix = "app.external.fred")
public record FredProperties(
        String apiKey,
        String baseUrl
) {
    public String baseUrlOrDefault() {
        return baseUrl == null || baseUrl.isBlank()
                ? "https://api.stlouisfed.org/fred"
                : baseUrl;
    }

    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }
}
