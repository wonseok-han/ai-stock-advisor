package com.nowini.notification.domain;

import java.math.BigDecimal;

public record NotificationSettingRequest(
        BigDecimal priceChangeThreshold,
        boolean onNewNews,
        boolean enabled
) {
}
