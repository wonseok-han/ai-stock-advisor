package com.aistockadvisor.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 설정.
 * Supabase JWKS 기반 JWT 검증 (비대칭 키). 기존 비인증 API는 permitAll 유지.
 * JWKS endpoint: https://{project-ref}.supabase.co/auth/v1/.well-known/jwks.json
 * design §7.2
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.supabase.url:}")
    private String supabaseUrl;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 기존 비인증 API
                        .requestMatchers("/api/v1/stocks/**").permitAll()
                        .requestMatchers("/api/v1/market/**").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        // 인증 필요 API
                        .requestMatchers("/api/v1/me").authenticated()
                        .requestMatchers("/api/v1/bookmarks/**").authenticated()
                        .requestMatchers("/api/v1/push/**").authenticated()
                        .requestMatchers("/api/v1/notifications/**").authenticated()
                        // 그 외 전부 허용
                        .anyRequest().permitAll()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder())
                                .jwtAuthenticationConverter(new SupabaseJwtConverter())
                        )
                )
                .build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        if (supabaseUrl == null || supabaseUrl.isBlank()) {
            return token -> {
                throw new IllegalStateException("SUPABASE_URL not configured");
            };
        }
        String jwksUri = supabaseUrl + "/auth/v1/.well-known/jwks.json";
        return NimbusJwtDecoder.withJwkSetUri(jwksUri).build();
    }
}
