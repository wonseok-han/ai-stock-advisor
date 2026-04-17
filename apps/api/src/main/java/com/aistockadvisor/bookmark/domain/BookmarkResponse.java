package com.aistockadvisor.bookmark.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record BookmarkResponse(
        String ticker,
        String name,
        BigDecimal price,
        BigDecimal changePercent,
        OffsetDateTime createdAt
) {
}
