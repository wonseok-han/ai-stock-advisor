package com.aistockadvisor.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * 가격 변동 푸시 알림 중복 억제 설정.
 * application.yml: app.notification.dedup.*
 */
@ConfigurationProperties(prefix = "app.notification.dedup")
public record NotificationDedupProperties(
        BigDecimal resetRatio,
        Duration cooldown
) {
    public NotificationDedupProperties {
        if (resetRatio == null) resetRatio = new BigDecimal("0.6");
        if (cooldown == null) cooldown = Duration.ofHours(4);
    }
}
