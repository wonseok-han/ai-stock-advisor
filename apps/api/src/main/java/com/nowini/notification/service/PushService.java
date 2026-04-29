package com.nowini.notification.service;

import com.nowini.notification.infra.PushSubscriptionEntity;
import com.nowini.notification.infra.PushSubscriptionRepository;
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

    @Value("${app.push.vapid.subject:mailto:admin@nowini.com}")
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

    /** url 없는 기존 시그니처 — 내부적으로 4-arg 에 null 위임. */
    public boolean sendToUser(UUID userId, String title, String body) {
        return sendToUser(userId, title, body, null);
    }

    /**
     * 사용자의 모든 구독 endpoint로 푸시 발송 시도.
     *
     * @param url 클릭 시 이동할 경로 (예: "/stocks/AAPL"). null/빈 문자열이면 sw 에서 "/" 기본.
     * @return true 면 1개 이상 endpoint 에 발송 성공. false 면 발송된 게 없음
     *         (webpush 미초기화 / 구독자 0명 / 모든 시도 실패).
     *         호출측은 이 값을 기준으로 "발송 성공" 상태를 저장해야 한다.
     */
    public boolean sendToUser(UUID userId, String title, String body, String url) {
        if (webPushService == null) {
            log.debug("Push disabled — skipping notification for user {}", userId);
            return false;
        }
        List<PushSubscriptionEntity> subs = subscriptionRepo.findByUserId(userId);
        if (subs.isEmpty()) {
            return false;
        }
        String payload = buildPayload(title, body, url);

        int success = 0;
        for (PushSubscriptionEntity sub : subs) {
            try {
                Notification notification = new Notification(
                        sub.getEndpoint(), sub.getP256dh(), sub.getAuth(), payload);
                webPushService.send(notification);
                success++;
            } catch (Exception e) {
                log.warn("Push send failed for endpoint {}: {}", sub.getEndpoint(), e.getMessage());
            }
        }
        return success > 0;
    }

    /** Web Push JSON payload 빌드. 테스트 가시성을 위해 package-private. */
    static String buildPayload(String title, String body, String url) {
        String escapedTitle = title.replace("\"", "\\\"");
        String escapedBody = body.replace("\"", "\\\"");
        if (url == null || url.isBlank()) {
            return "{\"title\":\"%s\",\"body\":\"%s\",\"icon\":\"/icon.svg\"}"
                    .formatted(escapedTitle, escapedBody);
        }
        String escapedUrl = url.replace("\"", "\\\"");
        return "{\"title\":\"%s\",\"body\":\"%s\",\"icon\":\"/icon.svg\",\"url\":\"%s\"}"
                .formatted(escapedTitle, escapedBody, escapedUrl);
    }

    public boolean isEnabled() {
        return webPushService != null;
    }

    public String getVapidPublicKey() {
        return vapidPublicKey;
    }
}
