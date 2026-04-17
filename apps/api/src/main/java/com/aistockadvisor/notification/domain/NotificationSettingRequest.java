package com.aistockadvisor.notification.domain;

import java.math.BigDecimal;

public record NotificationSettingRequest(
        BigDecimal priceChangeThreshold,
        boolean onNewNews,
        boolean onSignalChange,
        boolean enabled
) {
}
