package com.aistockadvisor.stock.domain;

import java.math.BigDecimal;
import java.util.List;

public record AnalystEstimates(
        Rating rating,
        PriceTarget priceTarget,
        List<EarningsQuarter> earnings
) {

    public record Rating(
            BigDecimal score,
            String label,
            String labelKo,
            Integer totalAnalysts,
            Distribution distribution
    ) {
        public record Distribution(
                int strongBuy, int buy, int hold, int sell, int strongSell
        ) {}
    }

    public record PriceTarget(
            BigDecimal current,
            BigDecimal high,
            BigDecimal low,
            BigDecimal mean,
            BigDecimal median,
            BigDecimal upsidePercent
    ) {}

    public record EarningsQuarter(
            String quarter,
            BigDecimal epsActual,
            BigDecimal epsEstimate,
            BigDecimal surprisePercent,
            String result
    ) {}

    public static String ratingLabel(BigDecimal score) {
        if (score == null) return null;
        double v = score.doubleValue();
        if (v <= 1.5) return "Strong Buy";
        if (v <= 2.5) return "Buy";
        if (v <= 3.5) return "Hold";
        if (v <= 4.5) return "Sell";
        return "Strong Sell";
    }

    public static String ratingLabelKo(BigDecimal score) {
        if (score == null) return null;
        double v = score.doubleValue();
        if (v <= 1.5) return "적극 매수";
        if (v <= 2.5) return "매수";
        if (v <= 3.5) return "보유";
        if (v <= 4.5) return "매도";
        return "적극 매도";
    }

    public static String earningsResult(BigDecimal surprisePercent) {
        if (surprisePercent == null) return "MEET";
        double v = surprisePercent.doubleValue();
        if (v > 1.0) return "BEAT";
        if (v < -1.0) return "MISS";
        return "MEET";
    }
}
