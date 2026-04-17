package com.aistockadvisor.notification.service;

import com.aistockadvisor.notification.domain.NotificationSettingRequest;
import com.aistockadvisor.notification.domain.NotificationSettingResponse;
import com.aistockadvisor.notification.infra.NotificationSettingEntity;
import com.aistockadvisor.notification.infra.NotificationSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class NotificationSettingService {

    private final NotificationSettingRepository settingRepo;

    public NotificationSettingService(NotificationSettingRepository settingRepo) {
        this.settingRepo = settingRepo;
    }

    @Transactional(readOnly = true)
    public List<NotificationSettingResponse> list(UUID userId) {
        return settingRepo.findByUserId(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public NotificationSettingResponse upsert(UUID userId, String ticker, NotificationSettingRequest req) {
        String upper = ticker.toUpperCase();
        NotificationSettingEntity entity = settingRepo.findByUserIdAndTicker(userId, upper)
                .orElseGet(() -> settingRepo.save(new NotificationSettingEntity(userId, upper)));
        entity.update(req.priceChangeThreshold(), req.onNewNews(), req.onSignalChange(), req.enabled());
        settingRepo.save(entity);
        return toResponse(entity);
    }

    private NotificationSettingResponse toResponse(NotificationSettingEntity e) {
        return new NotificationSettingResponse(
                e.getTicker(), e.getPriceChangeThreshold(),
                e.isOnNewNews(), e.isOnSignalChange(), e.isEnabled());
    }
}
