package com.aistockadvisor.notification.service;

import com.aistockadvisor.notification.infra.PushSubscriptionEntity;
import com.aistockadvisor.notification.infra.PushSubscriptionRepository;
import nl.martijndwars.webpush.Notification;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.security.GeneralSecurityException;
import java.security.Security;
import java.util.List;
import java.util.UUID;

@Service
public class PushService {

    private static final Logger log = LoggerFactory.getLogger(PushService.class);

    @Value("${app.push.vapid.public-key:}")
    private String vapidPublicKey;

    @Value("${app.push.vapid.private-key:}")
    private String vapidPrivateKey;

    @Value("${app.push.vapid.subject:mailto:admin@aistockadvisor.com}")
    private String vapidSubject;

    private final PushSubscriptionRepository subscriptionRepo;
    private nl.martijndwars.webpush.PushService webPushService;

    public PushService(PushSubscriptionRepository subscriptionRepo) {
        this.subscriptionRepo = subscriptionRepo;
    }

    @PostConstruct
    public void init() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        if (vapidPublicKey.isBlank() || vapidPrivateKey.isBlank()) {
            log.warn("VAPID keys not configured — push notifications disabled");
            return;
        }
        try {
            webPushService = new nl.martijndwars.webpush.PushService(vapidPublicKey, vapidPrivateKey, vapidSubject);
            log.info("Web Push service initialized");
        } catch (GeneralSecurityException e) {
            log.error("Failed to initialize Web Push service: {}", e.getMessage());
        }
    }

    @Transactional
    public void subscribe(UUID userId, String endpoint, String p256dh, String auth) {
        if (subscriptionRepo.existsByUserIdAndEndpoint(userId, endpoint)) {
            return;
        }
        subscriptionRepo.save(new PushSubscriptionEntity(userId, endpoint, p256dh, auth));
    }

    @Transactional
    public void unsubscribe(UUID userId, String endpoint) {
        subscriptionRepo.deleteByUserIdAndEndpoint(userId, endpoint);
    }

    public void sendToUser(UUID userId, String title, String body) {
        if (webPushService == null) {
            log.debug("Push disabled — skipping notification for user {}", userId);
            return;
        }
        List<PushSubscriptionEntity> subs = subscriptionRepo.findByUserId(userId);
        String payload = """
                {"title":"%s","body":"%s","icon":"/icon.svg"}""".formatted(
                title.replace("\"", "\\\""),
                body.replace("\"", "\\\""));

        for (PushSubscriptionEntity sub : subs) {
            try {
                Notification notification = new Notification(
                        sub.getEndpoint(), sub.getP256dh(), sub.getAuth(), payload);
                webPushService.send(notification);
            } catch (Exception e) {
                log.warn("Push send failed for endpoint {}: {}", sub.getEndpoint(), e.getMessage());
            }
        }
    }

    public boolean isEnabled() {
        return webPushService != null;
    }

    public String getVapidPublicKey() {
        return vapidPublicKey;
    }
}
