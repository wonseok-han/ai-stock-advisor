package com.aistockadvisor.common.security;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.security.Principal;
import java.util.UUID;

/**
 * 컨트롤러에서 인증된 사용자 정보를 추출하는 유틸리티.
 */
public final class AuthenticatedUser {

    private AuthenticatedUser() {
    }

    /** Principal에서 Supabase user_id (UUID) 추출. */
    public static UUID userId(Principal principal) {
        return UUID.fromString(principal.getName());
    }

    /** Principal에서 이메일 추출 (Supabase JWT email claim). */
    public static String email(Principal principal) {
        if (principal instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            return jwt.getClaimAsString("email");
        }
        return null;
    }
}
