package com.aistockadvisor.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import com.aistockadvisor.common.web.RateLimitFilter;
import org.springframework.security.config.Customizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 설정.
 * 두 개의 SecurityFilterChain 으로 분리:
 *   1) 인증 필요 API → JWT 검증 (JWKS, ES256)
 *   2) 나머지 공개 API → JWT 필터 없이 permitAll
 *
 * Supabase Auth v2 는 ES256(ECDSA P-256)으로 JWT 서명.
 * NimbusJwtDecoder 기본값이 RS256이므로 반드시 jwsAlgorithms(ES256)을 명시해야 함.
 *
 * 양쪽 체인 모두 .cors(withDefaults()) 적용 — WebCorsConfig 의 CorsConfigurationSource 빈 사용.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.supabase.url:}")
    private String supabaseUrl;

    @Value("${app.admin.username:admin}")
    private String adminUsername;

    @Value("${app.admin.password:}")
    private String adminPassword;

    private final RateLimitFilter rateLimitFilter;

    public SecurityConfig(RateLimitFilter rateLimitFilter) {
        this.rateLimitFilter = rateLimitFilter;
    }

    /** 운영자 API — HTTP Basic Auth 로 보호 (/api/admin/**) */
    @Bean
    @Order(0)
    public SecurityFilterChain adminFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/api/admin/**")
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().hasRole("ADMIN"))
                .httpBasic(Customizer.withDefaults())
                .userDetailsService(adminUserDetailsService())
                .build();
    }

    /** 인증 필요 API — JWT 검증 적용 */
    @Bean
    @Order(1)
    public SecurityFilterChain protectedFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/api/v1/me", "/api/v1/bookmarks/**", "/api/v1/push/subscribe", "/api/v1/push/unsubscribe", "/api/v1/notifications/**", "/api/v1/stocks/*/ai-signal")
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder())
                                .jwtAuthenticationConverter(new SupabaseJwtConverter())
                        )
                )
                .build();
    }

    /**
     * 환경변수 {@code ADMIN_PASSWORD} 가 비어있으면 admin 접근을 즉시 거부 (안전한 기본값).
     * NoOp 암호 encoder 는 환경변수 평문 비교 목적. 프로덕션에선 반드시 강력한 난수로 설정.
     */
    @Bean
    public UserDetailsService adminUserDetailsService() {
        if (adminPassword == null || adminPassword.isBlank()) {
            return username -> {
                throw new org.springframework.security.core.userdetails.UsernameNotFoundException(
                        "admin disabled: set ADMIN_PASSWORD to enable /api/admin/**");
            };
        }
        UserDetails admin = User.withUsername(adminUsername)
                .password("{noop}" + adminPassword)
                .roles("ADMIN")
                .build();
        return new InMemoryUserDetailsManager(admin);
    }

    /** 공개 API — JWT 필터 없음, 토큰 있어도 검증하지 않음 */
    @Bean
    @Order(2)
    public SecurityFilterChain publicFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
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
        return NimbusJwtDecoder.withJwkSetUri(jwksUri)
                .jwsAlgorithms(algs -> {
                    algs.add(SignatureAlgorithm.ES256);
                    algs.add(SignatureAlgorithm.RS256);
                })
                .build();
    }
}
