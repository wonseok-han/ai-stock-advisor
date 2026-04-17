package com.aistockadvisor.common.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

/**
 * Supabase JWT → Spring Security Authentication 변환.
 * sub claim → principal, role claim → authority.
 */
public class SupabaseJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String role = jwt.getClaimAsString("role");
        List<SimpleGrantedAuthority> authorities = role != null
                ? List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                : List.of();

        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }
}
