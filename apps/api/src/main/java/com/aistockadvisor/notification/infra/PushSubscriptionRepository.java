package com.aistockadvisor.notification.infra;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscriptionEntity, Long> {

    List<PushSubscriptionEntity> findByUserId(UUID userId);

    void deleteByUserIdAndEndpoint(UUID userId, String endpoint);

    boolean existsByUserIdAndEndpoint(UUID userId, String endpoint);

    void deleteByUserId(UUID userId);
}
