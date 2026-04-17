package com.aistockadvisor.auth.controller;

import com.aistockadvisor.auth.domain.MeResponse;
import com.aistockadvisor.common.security.AuthenticatedUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/**
 * 인증 관련 API.
 * GET /api/v1/me — JWT에서 사용자 정보 반환.
 */
@RestController
@RequestMapping("/api/v1")
public class AuthController {

    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(Principal principal) {
        return ResponseEntity.ok(new MeResponse(
                AuthenticatedUser.userId(principal),
                AuthenticatedUser.email(principal)
        ));
    }
}
