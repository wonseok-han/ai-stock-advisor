package com.aistockadvisor.stock.infra.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Finnhub API 연결 설정. application.yml: app.external.finnhub.*
 */
@ConfigurationProperties(prefix = "app.external.finnhub")
public record FinnhubProperties(
        String apiKey,
        String baseUrl
) {
}
