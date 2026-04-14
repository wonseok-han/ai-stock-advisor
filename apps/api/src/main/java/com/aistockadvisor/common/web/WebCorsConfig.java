package com.aistockadvisor.common.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS 설정.
 * application.yml 의 app.cors.allowed-origins 를 읽어 /api/** 경로에 허용 origin 적용.
 * 콤마 구분 복수 origin 지원. MVP 는 credentials 비활성 (JWT Phase 4 이후 재검토).
 */
@Configuration
public class WebCorsConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public WebCorsConfig(
            @Value("${app.cors.allowed-origins:http://localhost:3000}") String origins) {
        this.allowedOrigins = origins.isBlank()
                ? new String[]{"http://localhost:3000"}
                : origins.split("\\s*,\\s*");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("X-Request-Id")
                .maxAge(3600);
    }
}
