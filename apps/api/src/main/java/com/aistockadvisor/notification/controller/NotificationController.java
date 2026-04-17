package com.aistockadvisor.notification.controller;

import com.aistockadvisor.common.security.AuthenticatedUser;
import com.aistockadvisor.notification.domain.NotificationSettingRequest;
import com.aistockadvisor.notification.domain.NotificationSettingResponse;
import com.aistockadvisor.notification.service.NotificationSettingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationSettingService settingService;

    public NotificationController(NotificationSettingService settingService) {
        this.settingService = settingService;
    }

    @GetMapping("/settings")
    public List<NotificationSettingResponse> list(Principal principal) {
        UUID userId = AuthenticatedUser.userId(principal);
        return settingService.list(userId);
    }

    @PutMapping("/settings/{ticker}")
    public NotificationSettingResponse upsert(
            @PathVariable String ticker,
            @Valid @RequestBody NotificationSettingRequest req,
            Principal principal) {
        UUID userId = AuthenticatedUser.userId(principal);
        return settingService.upsert(userId, ticker, req);
    }

    @DeleteMapping("/settings/{ticker}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String ticker, Principal principal) {
        UUID userId = AuthenticatedUser.userId(principal);
        settingService.delete(userId, ticker);
    }
}
