package com.nowini.notification.domain;

import java.math.BigDecimal;

public record NotificationSettingResponse(
        String ticker,
        BigDecimal priceChangeThreshold,
        boolean onNewNews,
        boolean enabled
) {
}
