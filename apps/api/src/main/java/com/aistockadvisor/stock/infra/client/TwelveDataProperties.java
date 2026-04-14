package com.aistockadvisor.stock.infra.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Twelve Data API 연결 설정. application.yml: app.external.twelvedata.*
 * OHLCV 캔들 전용 (Finnhub 무료 /stock/candle 403 우회).
 */
@ConfigurationProperties(prefix = "app.external.twelvedata")
public record TwelveDataProperties(
        String apiKey,
        String baseUrl
) {
}
