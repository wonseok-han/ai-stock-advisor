package com.nowini.sec.domain;

import java.util.List;

public record SecFinancials(
        String ticker,
        List<QuarterValue> revenueHistory,
        List<QuarterValue> netIncomeHistory,
        String unit
) {
    public record QuarterValue(String quarter, double value) {}
}
