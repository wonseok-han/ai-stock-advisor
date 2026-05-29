package com.nowini.stock.infra.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Webshare 프록시 리스트 API 설정.
 * <p>
 * api-key가 비어 있으면 Webshare 연동은 비활성화되고,
 * 기존 {@code app.yahoo.proxy-url}(YAHOO_PROXY_URL) 수동 주입 방식이 fallback으로 사용된다.
 *
 * @param apiKey       Webshare API 토큰 (Authorization: Token &lt;apiKey&gt;)
 * @param baseUrl      Webshare API 베이스 URL
 * @param mode         proxy list 모드 (direct | backbone)
 * @param countryCodes ISO 3166-1 alpha-2 콤마구분 (빈값 = 전체)
 * @param pageSize     페이지당 프록시 수
 */
@ConfigurationProperties(prefix = "app.yahoo.webshare")
public record WebshareProperties(
        String apiKey,
        String baseUrl,
        String mode,
        String countryCodes,
        Integer pageSize
) {
    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }
}
