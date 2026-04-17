package com.aistockadvisor.notification.controller;

import com.aistockadvisor.common.security.AuthenticatedUser;
import com.aistockadvisor.notification.domain.PushSubscribeRequest;
import com.aistockadvisor.notification.domain.PushUnsubscribeRequest;
import com.aistockadvisor.notification.service.PushService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/push")
public class PushController {

    private final PushService pushService;

    public PushController(PushService pushService) {
        this.pushService = pushService;
    }

    @PostMapping("/subscribe")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Boolean> subscribe(@Valid @RequestBody PushSubscribeRequest req, Principal principal) {
        UUID userId = AuthenticatedUser.userId(principal);
        pushService.subscribe(userId, req.endpoint(), req.keys().p256dh(), req.keys().auth());
        return Map.of("subscribed", true);
    }

    @DeleteMapping("/unsubscribe")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@Valid @RequestBody PushUnsubscribeRequest req, Principal principal) {
        UUID userId = AuthenticatedUser.userId(principal);
        pushService.unsubscribe(userId, req.endpoint());
    }

    @GetMapping("/vapid-key")
    public Map<String, String> vapidKey() {
        return Map.of("publicKey", pushService.getVapidPublicKey());
    }
}
