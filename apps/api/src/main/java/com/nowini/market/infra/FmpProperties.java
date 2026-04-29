package com.nowini.market.infra;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.external.fmp")
public record FmpProperties(
        String apiKey,
        String baseUrl
) {
}
