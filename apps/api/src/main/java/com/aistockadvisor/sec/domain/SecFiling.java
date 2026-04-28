package com.aistockadvisor.sec.domain;

import java.time.LocalDate;

public record SecFiling(
        String ticker,
        String form,
        String title,
        LocalDate filedAt,
        String eventCategory,
        int daysAgo,
        String documentUrl,
        String contentSummary,
        String summaryKo
) {}
