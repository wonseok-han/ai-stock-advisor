package com.aistockadvisor.notification.domain;

import java.math.BigDecimal;

public record NotificationSettingResponse(
        String ticker,
        BigDecimal priceChangeThreshold,
        boolean onNewNews,
        boolean onSignalChange,
        boolean enabled
) {
}
