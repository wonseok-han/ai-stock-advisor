package com.aistockadvisor.auth.controller;

import com.aistockadvisor.auth.domain.DeleteAccountRequest;
import com.aistockadvisor.auth.domain.MeResponse;
import com.aistockadvisor.auth.service.AccountService;
import com.aistockadvisor.common.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

/**
 * 인증 관련 API.
 * GET /api/v1/me — JWT에서 사용자 정보 반환.
 * DELETE /api/v1/me — 회원 탈퇴 (soft delete).
 */
@RestController
@RequestMapping("/api/v1")
public class AuthController {

    private final AccountService accountService;

    public AuthController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(Principal principal) {
        return ResponseEntity.ok(new MeResponse(
                AuthenticatedUser.userId(principal),
                AuthenticatedUser.email(principal)
        ));
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(@RequestBody(required = false) DeleteAccountRequest req,
                               Principal principal) {
        UUID userId = AuthenticatedUser.userId(principal);
        String email = AuthenticatedUser.email(principal);
        String reason = req != null ? req.reason() : null;
        accountService.deleteAccount(userId, email, reason);
    }
}
