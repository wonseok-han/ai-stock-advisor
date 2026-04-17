package com.aistockadvisor.notification.infra;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationSettingRepository extends JpaRepository<NotificationSettingEntity, Long> {

    List<NotificationSettingEntity> findByUserId(UUID userId);

    Optional<NotificationSettingEntity> findByUserIdAndTicker(UUID userId, String ticker);

    List<NotificationSettingEntity> findByTickerAndEnabledTrue(String ticker);
}
